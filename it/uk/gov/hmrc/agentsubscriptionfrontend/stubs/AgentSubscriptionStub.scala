/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status
import uk.gov.hmrc.agentsubscriptionfrontend.models.SubscriptionRequest
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

object AgentSubscriptionStub {

  def withMatchingUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)))
  }

  def withNonMatchingUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.NOT_FOUND)))
  }

  def withErrorForUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.INTERNAL_SERVER_ERROR)))
  }

  def subscriptionSuccess(utr: String, request: SubscriptionRequest ): Unit = {
    stubFor(subscriptionRequestFor(utr, request)
              .willReturn(aResponse()
                .withStatus(201)
                  .withBody(
                    s"""
                       |{
                       |  "arn": "ARN00001"
                       |}
                     """.stripMargin)))
  }

  def subscriptionConflict(utr: String, request: SubscriptionRequest ): Unit = {
    stubFor(subscriptionRequestFor(utr, request)
      .willReturn(aResponse()
        .withStatus(409)))
  }

  def subscriptionForbidden(utr: String, request: SubscriptionRequest ): Unit = {
    stubFor(subscriptionRequestFor(utr, request)
      .willReturn(aResponse()
        .withStatus(403)))
  }

  private def subscriptionRequestFor(utr: String, request: SubscriptionRequest) = {
    val agency = request.agency
    val address = agency.address
    post(urlEqualTo(s"/agent-subscription/subscription"))
      .withRequestBody(equalToJson(s"""
           |{
           |  "utr": "${request.utr}",
           |  "knownFacts": {
           |    "postcode": "${request.knownFacts.postcode}"
           |  },
           |  "agency": {
           |    "name": "${agency.name}",
           |    "address": {
           |      "addressLine1": "${address.addressLine1}",
           |      "addressLine2": "${address.addressLine2}",
           |      ${address.addressLine3.map(l => s""""addressLine3":"$l",""") getOrElse ""}
           |      ${address.addressLine4.map(l => s""""addressLine4":"$l",""") getOrElse ""}
           |      "postcode": "${address.postcode}",
           |      "countryCode": "${address.countryCode}"
           |    },
           |    "telephone": "${agency.telephone}",
           |    "email": "${agency.email}"
           |  }
           |}
              """.stripMargin))
  }
}
