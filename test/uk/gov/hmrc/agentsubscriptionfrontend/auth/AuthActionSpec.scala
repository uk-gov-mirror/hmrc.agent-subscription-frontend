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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.CheckAgencyController
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.{TestAppConfig, TestMessagesApi}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, Authority, ConfidenceLevel}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthActionSpec extends UnitSpec with MockitoSugar {

  implicit val appConfig: AppConfig = TestAppConfig

  "AuthorisedWithAgent" should {
    "propagate errors that occur when checking affinity group (APB-493-3)" in {

      val authConnector = mock[AuthConnector]
      val agentSubscriptionConnector = mock[AgentSubscriptionConnector]
      val sessionStoreService = mock[SessionStoreService]
      val failure = Upstream5xxResponse("failure in auth", 500, 500)
      when(authConnector.currentAuthority(any[HeaderCarrier])).thenReturn(Future successful Some(authority))
      when(authConnector.getUserDetails(any[AuthContext])(any[HeaderCarrier], any[HttpReads[HttpResponse]])).thenReturn(Future failed failure)

      val controller = new CheckAgencyController(TestMessagesApi.testMessagesApi, authConnector, agentSubscriptionConnector, sessionStoreService)

      intercept[Upstream5xxResponse] {
        val eventualResult: Future[Result] = controller.showCheckAgencyStatus(mockRequestWithMockAuthSession)
        await(eventualResult)
      } shouldBe failure

    }
  }

  private val fakeAuthorityUri = "fakeUser"

  private def mockRequestWithMockAuthSession = {
    val request = mock[Request[AnyContent]]
    when(request.session).thenReturn(Session(Map(
      SessionKeys.userId -> fakeAuthorityUri,
      SessionKeys.token -> "fakeToken")))
    when(request.headers).thenReturn(Headers())
    request
  }

  private val authority = {
    val authority = mock[Authority]
    when(authority.uri).thenReturn(fakeAuthorityUri)
    when(authority.accounts).thenReturn(mock[Accounts])
    when(authority.confidenceLevel).thenReturn(ConfidenceLevel.L50)
    authority
  }
}
