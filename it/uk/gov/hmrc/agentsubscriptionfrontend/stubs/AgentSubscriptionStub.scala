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

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock.{request, _}
import play.api.http.Status
import uk.gov.hmrc.agentmtdidentifiers.model.{Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{CompanyRegistrationNumber, CompletePartialSubscriptionBody, SubscriptionRequest}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

object AgentSubscriptionStub {
  private def response(isSubscribedToAgentServices: Boolean, isSubscribedToETMP: Boolean) =
    s"""
       |{
       |  "taxpayerName": "My Agency",
       |  "isSubscribedToAgentServices": $isSubscribedToAgentServices,
       |  "isSubscribedToETMP": $isSubscribedToETMP,
       |  "address": {
       |        "addressLine1": "AddressLine1 A",
       |        "addressLine2": "AddressLine2 A",
       |        "addressLine3": "AddressLine3 A",
       |        "addressLine4": "AddressLine4 A",
       |        "countryCode": "GB",
       |        "postalCode": "AA11AA"
       |    },
       |    "emailAddress": "someone@example.com"
       |}""".stripMargin

  private def noOrganisationNameResponse(isSubscribedToAgentServices: Boolean, isSubscribedToETMP: Boolean) =
    s"""
      |{
      |  "isSubscribedToAgentServices": false,
      |  "isSubscribedToETMP": $isSubscribedToETMP,
      |  "address": {
      |        "addressLine1": "AddressLine1 A",
      |        "addressLine2": "AddressLine2 A",
      |        "addressLine3": "AddressLine3 A",
      |        "addressLine4": "AddressLine4 A",
      |        "countryCode": "GB",
      |        "postalCode": "AA11AA"
      |    },
      |    "emailAddress": "someone@example.com"
      |
      |}""".stripMargin

  def withMatchingUtrAndPostcode(utr: Utr, postcode: String, isSubscribedToAgentServices: Boolean = false, isSubscribedToETMP: Boolean = false): Unit =
    withMatchingUtrAndPostcodeAndBody(utr, postcode, response(isSubscribedToAgentServices, isSubscribedToETMP))

  def withNoOrganisationName(utr: Utr, postcode: String,isSubscribedToAgentServices: Boolean = false, isSubscribedToETMP: Boolean = false): Unit =
    withMatchingUtrAndPostcodeAndBody(utr, postcode, noOrganisationNameResponse(isSubscribedToAgentServices, isSubscribedToETMP))

  private def withMatchingUtrAndPostcodeAndBody(utr: Utr, postcode: String, responseBody: String): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(responseBody)))

  def withNonMatchingUtrAndPostcode(utr: Utr, postcode: String): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(aResponse()
          .withStatus(Status.NOT_FOUND)))

  def withErrorForUtrAndPostcode(utr: Utr, postcode: String): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(aResponse()
          .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def withMatchingCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
        .willReturn(aResponse()
          .withStatus(Status.OK)))

  def withNonMatchingCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
        .willReturn(aResponse()
          .withStatus(Status.NOT_FOUND)))

  def withErrorForCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
        .willReturn(aResponse()
          .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def withMatchingVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(dateOfReg.toString)}"))
        .willReturn(aResponse()
          .withStatus(Status.OK)))

  def withNonMatchingVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(dateOfReg.toString)}"))
        .willReturn(aResponse()
          .withStatus(Status.NOT_FOUND)))

  def withErrorForVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): Unit =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(dateOfReg.toString)}"))
        .willReturn(aResponse()
          .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def partialSubscriptionWillSucceed(request: CompletePartialSubscriptionBody, arn: String = "ARN00001"): Unit =
    stubFor(
      partialSubscriptionFixRequestFor(request)
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "arn": "$arn"
                         |}
                     """.stripMargin)))

  def partialSubscriptionWillReturnStatus(request: CompletePartialSubscriptionBody, responseCode: Int): Unit =
    stubFor(
      partialSubscriptionFixRequestFor(request)
        .willReturn(
          aResponse()
            .withStatus(responseCode)))

  def subscriptionWillSucceed(utr: Utr, request: SubscriptionRequest, arn: String = "ARN00001"): Unit =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody(s"""
                         |{
                         |  "arn": "$arn"
                         |}
                     """.stripMargin)))

  def subscriptionWillConflict(utr: Utr, request: SubscriptionRequest): Unit =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(409)))

  def subscriptionWillBeForbidden(utr: Utr, request: SubscriptionRequest): Unit =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(403)))

  def subscriptionAttemptWillReturnHttpCode(utr: Utr, request: SubscriptionRequest, code: Int): Unit =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(code)))

  def subscriptionAttemptWillFail(utr: Utr, request: SubscriptionRequest): Unit =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(500)))

  private def subscriptionRequestFor(utr: Utr, request: SubscriptionRequest) = {
    val agency = request.agency
    val address = agency.address
    post(urlEqualTo(s"/agent-subscription/subscription"))
      .withRequestBody(equalToJson(s"""
                                      |{
                                      |  "utr": "${request.utr.value}",
                                      |  "knownFacts": {
                                      |    "postcode": "${request.knownFacts.postcode}"
                                      |  },
                                      |  "agency": {
                                      |    "name": "${agency.name}",
                                      |    "address": {
                                      |      "addressLine1": "${address.addressLine1}",
                                      |      ${address.addressLine2.map(l => s""""addressLine2":"$l",""") getOrElse ""}
                                      |      ${address.addressLine3.map(l => s""""addressLine3":"$l",""") getOrElse ""}
                                      |      ${address.addressLine4.map(l => s""""addressLine4":"$l",""") getOrElse ""}
                                      |      "postcode": "${address.postcode}",
                                      |      "countryCode": "${address.countryCode}"
                                      |    },
                                      |    "email": "${agency.email}"
                                      |  }
                                      |  ${request.amlsDetails.map{ad =>
                                      s""","amlsDetails" : {
                                      |     "supervisoryBody" : "${ad.supervisoryBody}",
                                      |     "membershipNumber" : "${ad.membershipNumber}",
                                      |     "membershipExpiresOn" : "${ad.membershipExpiresOn}"
                                      |   }"""
                                          }.getOrElse("")}
                                      |}""".stripMargin))
  }

  private def partialSubscriptionFixRequestFor(request: CompletePartialSubscriptionBody) = {
    put(urlEqualTo(s"/agent-subscription/subscription"))
      .withRequestBody(equalToJson(s"""
                                      |{
                                      |  "utr": "${request.utr.value}",
                                      |  "knownFacts": {
                                      |    "postcode": "${request.knownFacts.postcode}"
                                      |   }
                                      |}""".stripMargin))
  }
}