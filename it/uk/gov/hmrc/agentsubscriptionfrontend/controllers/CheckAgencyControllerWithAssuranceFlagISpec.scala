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
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.isEnrolledForNonMtdServices
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers.subscribingAgent
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

class CheckAgencyControllerWithAssuranceFlagISpec extends CheckAgencyControllerISpec {
  override def agentAssuranceFlag = true

  "checkAgencyStatus with the agentAssuranceFlag set to true" should {
    "redirect to confirm agency page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult shouldBe Some(
        KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false)
      )
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(true), passSaAgentAssuranceCheck = Some(true))
      metricShouldExistsAndBeenUpdated("Count-Subscription-CheckAgency-Success")
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(true), passSaAgentAssuranceCheck = Some(true))
      metricShouldExistsAndBeenUpdated("Count-Subscription-CheckAgency-Success")
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "fail when a matching registration is found for the UTR and postcode for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(false), passSaAgentAssuranceCheck = Some(false))
    }

    "fail when the business registration found by agent-subscription is not already subscribed for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest().withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(false), passSaAgentAssuranceCheck = Some(false))
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed " +
      "for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "proceed to showConfirmYourAgency when there is not an acceptable number of PAYE client, but there is enough SA Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(false), passSaAgentAssuranceCheck = Some(true))
    }

    "proceed to showConfirmYourAgency when there in not an acceptable number of SA client, but there is enough PAYE Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = Some(true), passSaAgentAssuranceCheck = Some(false))
    }

    "redirect to setup incomplete when agent's utr is in the R2DW list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithUtrIsForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.setupIncomplete().url)
      verify(1, getRequestedFor(urlPathEqualTo(s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}")))
      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verifyCheckForAcceptableNumberOfPAYEClientsUrl(0)
      verifyCheckForAcceptableNumberOfSAClients(0)
    }

    "continue checks when UTR is not found the R2DW list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      verify(1, getRequestedFor(urlPathEqualTo(s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}")))
      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verifyCheckForAcceptableNumberOfPAYEClientsUrl(1)
      verifyCheckForAcceptableNumberOfSAClients(1)
    }

    "exception received due to missing config in R2DW" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithReturns404(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[IllegalStateException] shouldBe thrownBy(await(controller.checkAgencyStatus(request)))

      verify(1, getRequestedFor(urlPathEqualTo(s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}")))
      verifyCheckRefusalToDealWith(1, validUtr.value)
    }

    "proceed direct to showConfirmYourAgency and skip assurance checks " +
    "when agent's UTR is not in the Refusal to Deal With list but is in the Manually Assured Agents list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsManuallyAssured(validUtr.value)

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verify(1, getRequestedFor(urlPathEqualTo(s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}")))
      verifyCheckForAcceptableNumberOfPAYEClientsUrl(0)
      verifyCheckForAcceptableNumberOfSAClients(0)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = None, passSaAgentAssuranceCheck = None)
    }

    "perform all usual assurance checks when agent's UTR is not in the Manually Assured Agents list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      isEnrolledForNonMtdServices(subscribingAgent)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients

      implicit val request = authenticatedRequest(subscribingAgent).withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)

      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verify(1, getRequestedFor(urlPathEqualTo(s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}")))
      verifyCheckForAcceptableNumberOfPAYEClientsUrl(1)
      verifyCheckForAcceptableNumberOfSAClients(1)
    }
  }
}