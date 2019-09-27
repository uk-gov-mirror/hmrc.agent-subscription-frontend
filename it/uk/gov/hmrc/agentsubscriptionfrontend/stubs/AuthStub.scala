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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentsubscriptionfrontend.support.{SampleUser, SessionKeysForTesting}
import uk.gov.hmrc.http.SessionKeys

object AuthStub {
  def authIsDown(): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(aResponse()
          .withStatus(500)))

  def userIsNotAuthenticated(): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"SessionRecordNotFound\"")))

  def userHasInsufficientEnrolments(): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))

  def userLoggedInViaUnsupportedAuthProvider(): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"UnsupportedAuthProvider\"")))

  def userIsNotAnAgent(user: SampleUser): Seq[(String, String)] = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"UnsupportedAffinityGroup\"")))
    sessionKeysForMockAuth(user)
  }

  def userIsAuthenticated(user: SampleUser): Seq[(String, String)] = {
    val response =
      s"""{${user.allEnrolments},${user.affinityGroup},"optionalCredentials": {"providerId": "${user.userId}", "providerType": "GovernmentGateway"}${user.ninoRetrieval}}"""
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(response)))
    sessionKeysForMockAuth(user)
  }

  def authenticatedAgent(arn: String, providerId: String): StubMapping = {
    givenAuthorisedFor(
      s"""
         |{
         |  "authorise": [
         |    { "identifiers":[], "state":"Activated", "enrolment": "HMRC-AS-AGENT" },
         |    { "authProviders": ["GovernmentGateway"] }
         |  ],
         |  "retrieve":["authorisedEnrolments", "optionalCredentials"]
         |}
           """.stripMargin,
      s"""
         |{
         |"authorisedEnrolments": [
         |  { "key":"HMRC-AS-AGENT", "identifiers": [
         |    {"key":"AgentReferenceNumber", "value": "$arn"}
         |  ]}
         |],
         |"optionalCredentials": {"providerId": "$providerId", "providerType": "GovernmentGateway"}
         |}
          """.stripMargin
    )
  }

  def givenAuthorisedFor(payload: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(payload, true, true))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)))


  def sessionKeysForMockAuth(user: SampleUser): Seq[(String, String)] =
    Seq(SessionKeys.userId -> user.userId, SessionKeysForTesting.token -> "fakeToken")

}
