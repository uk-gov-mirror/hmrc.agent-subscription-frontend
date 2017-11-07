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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers.subscribingAgent

class SubscriptionControllerWithAssuranceFlagISpec extends SubscriptionControllerISpec {
  override def agentAssuranceFlag = true

  "checkAgencyStatus with the agentAssuranceFlag set to true" should {
    "show initial details page when both PAYE and SA checks pass" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      hasNoEnrolments(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients

      val result = await(controller.showInitialDetails(request))

      status(result) shouldBe 200

      sessionStoreService.currentSession.knownFactsResult shouldBe
        Some(KnownFactsResult(utr, knownFactsPostcode, "My Business", isSubscribedToAgentServices = false))
    }

    "show initial details page when PAYE check passes" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      hasNoEnrolments(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients

      val result = await(controller.showInitialDetails(request))

      status(result) shouldBe 200

      sessionStoreService.currentSession.knownFactsResult shouldBe
        Some(KnownFactsResult(utr, knownFactsPostcode, "My Business", isSubscribedToAgentServices = false))
    }

    "show initial details page when PAYE check fails but SA check passes" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      hasNoEnrolments(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients

      val result = await(controller.showInitialDetails(request))

      status(result) shouldBe 200

      sessionStoreService.currentSession.knownFactsResult shouldBe
        Some(KnownFactsResult(utr, knownFactsPostcode, "My Business", isSubscribedToAgentServices = false))
    }

    "return NotImplemented when both PAYE and SA checks fail" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      hasNoEnrolments(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients

      val result = await(controller.showInitialDetails(request))

      status(result) shouldBe 501
    }
  }
}