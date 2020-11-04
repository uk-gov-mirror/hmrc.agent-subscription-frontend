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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import uk.gov.hmrc.agentmtdidentifiers.model.{Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.domain.Nino
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

  def withMatchingUtrAndPostcode(
    utr: Utr,
    postcode: String,
    isSubscribedToAgentServices: Boolean = false,
    isSubscribedToETMP: Boolean = false): StubMapping =
    withMatchingUtrAndPostcodeAndBody(utr, postcode, response(isSubscribedToAgentServices, isSubscribedToETMP))

  def withNoOrganisationName(
    utr: Utr,
    postcode: String,
    isSubscribedToAgentServices: Boolean = false,
    isSubscribedToETMP: Boolean = false): StubMapping =
    withMatchingUtrAndPostcodeAndBody(
      utr,
      postcode,
      noOrganisationNameResponse(isSubscribedToAgentServices, isSubscribedToETMP))

  def withPartiallySubscribedAgent(
    utr: Utr,
    postcode: String,
    isSubscribedToAgentServices: Boolean = false,
    isSubscribedToETMP: Boolean = true): StubMapping =
    withMatchingUtrAndPostcodeAndBody(utr, postcode, response(isSubscribedToAgentServices, isSubscribedToETMP))

  private def withMatchingUtrAndPostcodeAndBody(utr: Utr, postcode: String, responseBody: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(responseBody)))

  def withNonMatchingUtrAndPostcode(utr: Utr, postcode: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(aResponse()
          .withStatus(Status.NOT_FOUND)))

  def withErrorForUtrAndPostcode(utr: Utr, postcode: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"))
        .willReturn(aResponse()
          .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def withMatchingCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
      .willReturn(aResponse()
        .withStatus(Status.OK)))

  def withNonMatchingCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
      .willReturn(aResponse()
        .withStatus(Status.NOT_FOUND)))

  def withErrorForCtUtrAndCrn(ctUtr: Utr, crn: CompanyRegistrationNumber): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/corporation-tax-utr/${encodePathSegment(ctUtr.value)}/crn/${encodePathSegment(crn.value)}"))
      .willReturn(aResponse()
        .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def withMatchingVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(
        dateOfReg.toString)}"))
      .willReturn(aResponse()
        .withStatus(Status.OK)))

  def withNonMatchingVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(
        dateOfReg.toString)}"))
      .willReturn(aResponse()
        .withStatus(Status.NOT_FOUND)))

  def withErrorForVrnAndDateOfReg(vrn: Vrn, dateOfReg: LocalDate): StubMapping =
    stubFor(get(urlEqualTo(
      s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(
        dateOfReg.toString)}"))
      .willReturn(aResponse()
        .withStatus(Status.INTERNAL_SERVER_ERROR)))

  def partialSubscriptionWillSucceed(request: CompletePartialSubscriptionBody, arn: String = "ARN00001"): StubMapping =
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

  def partialSubscriptionWillReturnStatus(request: CompletePartialSubscriptionBody, responseCode: Int): StubMapping =
    stubFor(
      partialSubscriptionFixRequestFor(request)
        .willReturn(aResponse()
          .withStatus(responseCode)))

  // temporary test stub while we investigate 'partially terminated bug' in ETMP for agents that have been terminated

  def partialSubscriptionWillFailAgentTerminated(request: CompletePartialSubscriptionBody, arn: String = "ARN00001"): StubMapping =
    stubFor(
      partialSubscriptionFixRequestFor(request)
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
            .withBody(
              s"""
                 |{
                 |"statusCode": 500,
                 |"message": "GET of '/registration/personal-details/utr/${request.utr}' returned 403. Response body: '{\"code\": \"AGENT_TERMINATED\", \"reason\": \"The remote endpoint has indicated that $arn is terminated\"}'"}'"
                 |""".stripMargin)
        )
    )

  def subscriptionWillSucceed(utr: Utr, request: SubscriptionRequest, arn: String = "ARN00001"): StubMapping =
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

  def subscriptionWillFailForTerminatedAgent(utr: Utr, request: SubscriptionRequest, arn: String = "ARN00001"): StubMapping =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
            .withBody(
              s"""
                 |{
                 |"statusCode": 500,
                 |"message": "GET of '/registration/personal-details/utr/${request.utr}' returned 403. Response body: '{\"code\": \"AGENT_TERMINATED\", \"reason\": \"The remote endpoint has indicated that $arn is terminated\"}'"}'"
                 |""".stripMargin)
        )
    )

  def subscriptionWillConflict(utr: Utr, request: SubscriptionRequest): StubMapping =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(409)))

  def subscriptionWillBeForbidden(utr: Utr, request: SubscriptionRequest): StubMapping =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(403)))

  def subscriptionAttemptWillReturnHttpCode(utr: Utr, request: SubscriptionRequest, code: Int): StubMapping =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(code)))

  def subscriptionAttemptWillFail(utr: Utr, request: SubscriptionRequest): StubMapping =
    stubFor(
      subscriptionRequestFor(utr, request)
        .willReturn(aResponse()
          .withStatus(500)))

  def givenDesignatoryDetailsForNino(nino: Nino, lastName: Option[String], dob: DateOfBirth): StubMapping =
    stubFor(
      get(
        urlEqualTo(
          s"/agent-subscription/citizen-details/${nino.value}/designatory-details"
        )).willReturn(
        aResponse()
          .withBody(s"""{
                       |       "etag" : "115",
                       |       "person" : {
                       |         "firstName" : "HIPPY",
                       |         "middleName" : "T",
                       |         ${lastName.map(name => s""" "lastName" : "$name", """).getOrElse("")}
                       |         "title" : "Mr",
                       |         "honours": "BSC",
                       |         "sex" : "M",
                       |         "dateOfBirth" : "${dob.value.toString}",
                       |         "nino" : "TW189213B",
                       |         "deceased" : false
                       |       },
                       |       "address" : {
                       |         "line1" : "26 FARADAY DRIVE",
                       |         "line2" : "PO BOX 45",
                       |         "line3" : "LONDON",
                       |         "postcode" : "CT1 1RQ",
                       |         "startDate": "2009-08-29",
                       |         "country" : "GREAT BRITAIN",
                       |         "type" : "Residential"
                       |       }
                       |}""".stripMargin)
          .withStatus(200))
    )

  def givenDesignatoryDetailsReturnsStatus(nino: Nino, status: Int): StubMapping =
    stubFor(
      get(
        urlEqualTo(
          s"/agent-subscription/citizen-details/${nino.value}/designatory-details"
        )).willReturn(aResponse().withStatus(status))
    )

  def givenCompaniesHouseNameCheckReturnsStatus(crn: CompanyRegistrationNumber, name: String, status: Int): StubMapping =
    stubFor(
      get(
        urlEqualTo(
          s"/agent-subscription/companies-house-api-proxy/company/${encodePathSegment(crn.value)}/officers/$name"
        )
      ).willReturn(aResponse().withStatus(status))
    )

  private def subscriptionRequestFor(utr: Utr, request: SubscriptionRequest) = {
    val agency = request.agency
    val address = agency.address
    post(urlEqualTo(s"/agent-subscription/subscription"))
      .withRequestBody(equalToJson(s"""
                                      |{
                                      |  "utr": "${utr.value}",
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
                                      |  },
                                      |  "langForEmail": "${request.langForEmail.fold("")(_.code)}"
                                      |  ${request.amlsDetails.get.details match {
                                        case Right(registeredDetails) =>
                                          s""","amlsDetails" : {
                                          |     "supervisoryBody" : "${request.amlsDetails.get.supervisoryBody}",
                                          |     "membershipNumber" : "${registeredDetails.membershipNumber}",
                                          |     "membershipExpiresOn" : "${registeredDetails.membershipExpiresOn}"
                                          |   }"""
                                        case Left(pendingDetails) =>
                                          s""","amlsDetails" : {
                                          |     "supervisoryBody" : "${request.amlsDetails.get.supervisoryBody}",
                                          |     "appliedOn" : "${pendingDetails.appliedOn}"
                                          |   }"""
                                      }}
                                      |}""".stripMargin))
  }

  private def partialSubscriptionFixRequestFor(request: CompletePartialSubscriptionBody) =
    put(urlEqualTo(s"/agent-subscription/subscription"))
      .withRequestBody(equalToJson(s"""
                                      |{
                                      |  "utr": "${request.utr.value}",
                                      |  "knownFacts": {
                                      |    "postcode": "${request.knownFacts.postcode}"
                                      |   },
                                      |   "langForEmail": "en"
                                      |}""".stripMargin))

}
