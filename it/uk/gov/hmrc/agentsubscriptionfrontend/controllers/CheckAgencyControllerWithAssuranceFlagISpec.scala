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

import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.isEnrolledForNonMtdServices
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers.subscribingAgent

class CheckAgencyControllerWithAssuranceFlagISpec extends CheckAgencyControllerISpec {
  override def agentAssuranceFlag = true

  "checkAgencyStatus with the agentAssuranceFlag set to true" should {
    "redirect to confirm agency page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult shouldBe Some(
        KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false)
      )
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = true, passSaAgentAssuranceCheck = true)
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = true, passSaAgentAssuranceCheck = true)
    }

    "store isSubscribedToAgentServices = true in session when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe true
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = true, passSaAgentAssuranceCheck = true)
    }

    "fail when a matching registration is found for the UTR and postcode for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = false, passSaAgentAssuranceCheck = false)
    }

    "fail when the business registration found by agent-subscription is not already subscribed for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest().withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = false, passSaAgentAssuranceCheck = false)
    }

    "fail when the business registration found by agent-subscription is already subscribed for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = false, passSaAgentAssuranceCheck = false)
    }

    "proceed to showConfirmYourAgency when there in not an acceptable number of PAYE client, but there is enough SA Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = false, passSaAgentAssuranceCheck = true)
    }

    "proceed to showConfirmYourAgency when there in not an acceptable number of SA client, but there is enough PAYE Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients


      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = true, passSaAgentAssuranceCheck = false)
    }
  }
}