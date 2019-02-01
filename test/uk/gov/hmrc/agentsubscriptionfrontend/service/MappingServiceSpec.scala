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

import org.mockito.Mockito._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.{IsEligible, IsNotEligible, UnknownEligibility}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.agentsubscriptionfrontend.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class MappingServiceSpec extends UnitSpec with ResettingMockitoSugar {
  private implicit val hc = HeaderCarrier()
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val mockAppConfig = resettingMock[AppConfig]
  private val mockMappingConnector = resettingMock[MappingConnector]
  private val mockSessionStoreService = resettingMock[SessionStoreService]
  private val mockChainedSessionDetailsRepository = resettingMock[ChainedSessionDetailsRepository]

  private val mappingService =
    new MappingService(
      mockAppConfig,
      mockMappingConnector,
      mockSessionStoreService,
      mockChainedSessionDetailsRepository)

  "captureTempMappingsPreSubscription" when {
    "auto mapping is enabled" should {
      "return UnknownEligibility if session store does not contain known facts" in new AutoMappingEnabled {
        when(mockSessionStoreService.fetchKnownFactsResult).thenReturn(Future.successful(None))
        await(mappingService.captureTempMappingsPreSubscription) shouldBe UnknownEligibility
        verifyZeroInteractions(mockMappingConnector)
      }

      Seq(IsEligible, IsNotEligible, UnknownEligibility) foreach { eligibility =>
        s"return mapping outcome if session store contains known facts and mapping returns $eligibility" in new AutoMappingEnabled
        with KnownFactsInSessionStore {
          when(mockMappingConnector.createPreSubscription(Utr("9876543210"))).thenReturn(Future.successful(eligibility))
          await(mappingService.captureTempMappingsPreSubscription) shouldBe eligibility
        }
      }

      "return failed future if session store contains known facts but mapping fails" in new AutoMappingEnabled
      with KnownFactsInSessionStore {
        val someException = new Exception("Some mapping problem")
        when(mockMappingConnector.createPreSubscription(Utr("9876543210"))).thenReturn(Future.failed(someException))
        intercept[Exception] {
          await(mappingService.captureTempMappingsPreSubscription)
        } shouldBe someException
      }
    }

    "auto mapping is disabled" should {
      "return UnknownEligibility" in new AutoMappingDisabled {
        await(mappingService.captureTempMappingsPreSubscription) shouldBe UnknownEligibility
        verifyZeroInteractions(mockMappingConnector)
      }
    }
  }

  private trait AutoMappingEnabled {
    when(mockAppConfig.autoMapAgentEnrolments).thenReturn(true)
  }

  private trait AutoMappingDisabled {
    when(mockAppConfig.autoMapAgentEnrolments).thenReturn(false)
  }

  trait KnownFactsInSessionStore {
    val knownFactsResult =
      KnownFactsResult(
        Utr("9876543210"),
        "AA11AA",
        "Test organisation name",
        isSubscribedToAgentServices = true,
        Some(
          BusinessAddress(
            "AddressLine1 A",
            Some("AddressLine2 A"),
            Some("AddressLine3 A"),
            Some("AddressLine4 A"),
            Some("AA1AA"),
            "GB")),
        Some("someone@example.com")
      )
    when(mockSessionStoreService.fetchKnownFactsResult).thenReturn(Future.successful(Some(knownFactsResult)))
  }
}
