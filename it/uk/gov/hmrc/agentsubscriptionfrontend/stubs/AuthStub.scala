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
import uk.gov.hmrc.play.http.SessionKeys

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

  private def sessionKeysForMockAuth(user: SampleUser): Seq[(String, String)] = Seq(
    SessionKeys.userId -> user.authorityUri,
    SessionKeysForTesting.token -> "fakeToken")

}
