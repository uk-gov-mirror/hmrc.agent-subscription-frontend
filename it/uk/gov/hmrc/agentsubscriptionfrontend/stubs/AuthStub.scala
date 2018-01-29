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
import uk.gov.hmrc.agentsubscriptionfrontend.support.{SampleUser, SessionKeysForTesting}
import uk.gov.hmrc.http.SessionKeys

object AuthStub {
  def authIsDown(): Unit = {
    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
    )
  }

  def userIsNotAuthenticated(): Unit = {
    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(401)
      )
    )
  }

  /**
    * @return session keys required for the play-authorised-frontend library to
    *         recognise that the user is logged in
    */
  def userIsAuthenticated(user: SampleUser): Seq[(String, String)] = {
    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(user.authJson)
      )
    )

    stubFor(get(urlMatching("/auth/oid/[^/]+$"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(user.authJson)
      )
    )

    stubFor(get(urlEqualTo(user.userDetailsLink))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(user.userDetailsJson)
      )
    )

    sessionKeysForMockAuth(user)
  }

  def passcodeAuthorisationSucceeds(regime: String = "agent-subscription", otacToken: String = "dummy-otac-token"): Seq[(String, String)] = {
    stubPasscodeAuthorisation(regime, 200)

    Seq(SessionKeys.otacToken -> otacToken)
  }

  def passcodeAuthorisationFails(regime: String = "agent-subscription"): Unit = {
    stubPasscodeAuthorisation(regime, 404)
  }

  private def stubPasscodeAuthorisation(regime: String, status: Int) = {
    stubFor(get(urlEqualTo(s"/authorise/read/$regime"))
      .willReturn(
        aResponse()
          .withStatus(status)))
  }

  def isSubscribedToMtd(user: SampleUser): Unit = {
    stubFor(get(urlEqualTo(user.enrolmentsLink))
        .willReturn(
          aResponse()
              .withStatus(200)
              .withBody(
                s"""
                   |[{"key":"HMRC-AS-AGENT","identifiers":[{"key":"AgentReferenceNumber","value":"JARN1234567"}],"state":"Activated"}]
                 """.stripMargin
              )
        ))
  }

  def isSubscribedToMtdNotActivated(user: SampleUser): Unit = {
    stubFor(get(urlEqualTo(user.enrolmentsLink))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |[{"key":"HMRC-AS-AGENT","identifiers":[{"key":"AgentReferenceNumber","value":"JARN1234567"}],"state":"Not-Activated"}]
                 """.stripMargin
          )
      ))
  }

  def hasNoEnrolments(user: SampleUser): Unit = {
    stubFor(get(urlEqualTo(user.enrolmentsLink))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody("[]")
      ))
  }

  def isEnrolledForNonMtdServices(user: SampleUser): Unit = {
    stubFor(get(urlEqualTo(user.enrolmentsLink))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |[{"key":"IR-PAYE-AGENT","identifiers":[{"key":"IRAgentReference","value":"HZ1234"}],"state":"Activated"},
               | {"key":"IR-SA-AGENT","identifiers":[{"key":"IRAgentReference","value":"FOO1234"}],"state":"Activated"}]
         """.stripMargin
          )
      ))
  }

  private def sessionKeysForMockAuth(user: SampleUser): Seq[(String, String)] = Seq(
    SessionKeys.userId -> user.authorityUri,
    SessionKeysForTesting.token -> "fakeToken")

}
