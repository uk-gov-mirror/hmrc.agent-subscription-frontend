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

import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionService
import uk.gov.hmrc.agentsubscriptionfrontend.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import scala.concurrent.ExecutionContext

class CommonRoutingSpec extends UnitSpec with WithFakeApplication with ResettingMockitoSugar {
  private val mockMappingConnector = resettingMock[MappingConnector]
  private val mockSubscriptionService = resettingMock[SubscriptionService]
  private val mockAppConfig = resettingMock[AppConfig]

  private val commonRouting =
    new CommonRouting(mockMappingConnector, mockSubscriptionService, mockAppConfig)

  private val utr = Utr("9876543210")
  private implicit val hc = HeaderCarrier()
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  "handleAutoMapping" should {
    "handleAutoMapping decide whether a user can see showLinkClients page needed for mapping" when {
      implicit def request = FakeRequest()

      "autoMapping is ON and they ARE ELIGIBLE for mapping SHOW linkClients page " in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = await(commonRouting.handleAutoMapping(eligibleForMapping = Some(true)))
        redirectLocation(result).get shouldBe routes.SubscriptionController.showLinkClients().url
      }

      "autoMapping is ON and they are NOT ELIGIBLE for mapping hence why was NOT OFFERED the decision to add decision to session" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = await(commonRouting.handleAutoMapping(eligibleForMapping = Some(false)))
        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }

      "autoMapping is ON and chainedSessionDetails did NOT CACHE wasEligibleForMapping" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)

        val result = commonRouting.handleAutoMapping(eligibleForMapping = None)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }

      "autoMapping is OFF" in {
        when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)

        val result = commonRouting.handleAutoMapping(None)
        status(result) shouldBe 303

        redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
      }
    }
  }
}
