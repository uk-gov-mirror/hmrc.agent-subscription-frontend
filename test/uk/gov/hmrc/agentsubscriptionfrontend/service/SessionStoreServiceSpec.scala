/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SessionStoreServiceSpec extends UnitSpec with MockFactory {

  val utr = Utr("2000000000")
  val testPostcode = "AA1 1AA"

  val mockSessionStoreService = mock[SessionStoreService]
  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  "cacheContinueUrl and fetchContinueUrl" should {
    "cache a continue url and fetch it back" in {
      (mockSessionStoreService
        .cacheContinueUrl(_: ContinueUrl)(_: HeaderCarrier, _: ExecutionContext))
        .expects(ContinueUrl("/continue/url"), hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(Some(ContinueUrl("/continue/url"))))

      mockSessionStoreService.cacheContinueUrl(ContinueUrl("/continue/url"))
      val result = await(mockSessionStoreService.fetchContinueUrl)
      result shouldBe Some(ContinueUrl("/continue/url"))
    }
  }

  "cacheGoBackUrl and fetchGoBackUrl" should {
    "cache a go back url and fetch it back" in {
      (mockSessionStoreService
        .cacheGoBackUrl(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects("/go/back", hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchGoBackUrl(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(Some("/go/back")))

      mockSessionStoreService.cacheGoBackUrl("/go/back")
      val result = await(mockSessionStoreService.fetchGoBackUrl)
      result shouldBe Some("/go/back")
    }
  }

  "cacheIsChangingAnswers and fetchIsChangingAnswers" should {
    "cache whether a user is changing answers and fetch it back" in {
      (mockSessionStoreService
        .cacheIsChangingAnswers(_: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(true, hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchIsChangingAnswers(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(Some(true)))

      mockSessionStoreService.cacheIsChangingAnswers(true)
      val result = await(mockSessionStoreService.fetchIsChangingAnswers)
      result shouldBe Some(true)
    }
  }

  "cacheAgentSession and fetchAgentSession" should {
    val agentSession =
      AgentSession(businessType = Some(SoleTrader), utr = Some(utr), postcode = Some(Postcode(testPostcode)))
    "cache an agent session and fetch it back" in {
      (mockSessionStoreService
        .cacheAgentSession(_: AgentSession)(_: HeaderCarrier, _: ExecutionContext))
        .expects(agentSession, hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchAgentSession(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(Some(agentSession)))

      mockSessionStoreService.cacheAgentSession(agentSession)
      val result = await(mockSessionStoreService.fetchAgentSession)
      result shouldBe Some(agentSession)
    }
  }

  "remove" should {
    "clear the session" in {
      (mockSessionStoreService
        .cacheContinueUrl(_: ContinueUrl)(_: HeaderCarrier, _: ExecutionContext))
        .expects(ContinueUrl("/continue/url"), hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(Some(ContinueUrl("/continue/url"))))
      (mockSessionStoreService
        .remove()(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(()))
      (mockSessionStoreService
        .fetchContinueUrl(_: HeaderCarrier, _: ExecutionContext))
        .expects(hc, *)
        .returns(Future(None))

      mockSessionStoreService.cacheContinueUrl(ContinueUrl("/continue/url"))
      val fetchResult = await(mockSessionStoreService.fetchContinueUrl)
      fetchResult shouldBe Some(ContinueUrl("/continue/url"))

      mockSessionStoreService.remove()
      val fetchResultEmpty = await(mockSessionStoreService.fetchContinueUrl)
      fetchResultEmpty shouldBe None
    }
  }

}
