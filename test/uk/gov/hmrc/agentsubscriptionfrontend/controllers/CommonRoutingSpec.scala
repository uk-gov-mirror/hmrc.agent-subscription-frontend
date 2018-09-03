/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class CommonRoutingSpec extends UnitSpec with ResettingMockitoSugar with WithFakeApplication {

  private val mockSessionStoreService = resettingMock[SessionStoreService]
  private val mockAppConfig = resettingMock[AppConfig]

  private val commonRouting = new CommonRouting(mockSessionStoreService, mockAppConfig)

  private val arn = Arn("TARN00001")
  private implicit val hc = HeaderCarrier()
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val request = FakeRequest()

  "redirectUponSuccessfulSubscription" should {
    "redirect to showLinkClients" when {
      "autoMapping is on and they are eligible for mapping" in {
        when(mockSessionStoreService.fetchMappingEligible(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(true)))
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = commonRouting.redirectUponSuccessfulSubscription(arn)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showLinkClients().url
      }
    }

    "redirect to showSubscriptionComplete" when {
      "autoMapping is on and they are not eligible for mapping" in {
        when(mockSessionStoreService.fetchMappingEligible(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(false)))
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = commonRouting.redirectUponSuccessfulSubscription(arn)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "autoMapping is on and chainedSessionDetails did not cache wasEligibleForMapping" in {
        when(mockSessionStoreService.fetchMappingEligible(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(None))
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = commonRouting.redirectUponSuccessfulSubscription(arn)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "autoMapping is off" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)

        val result = commonRouting.redirectUponSuccessfulSubscription(arn)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }
    }

    "session store service returns an exception" in {
      when(mockSessionStoreService.fetchMappingEligible(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("mapping cache unavailable")))
      when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

      an[Exception] shouldBe thrownBy(await(commonRouting.redirectUponSuccessfulSubscription(arn)))
    }
  }
}
