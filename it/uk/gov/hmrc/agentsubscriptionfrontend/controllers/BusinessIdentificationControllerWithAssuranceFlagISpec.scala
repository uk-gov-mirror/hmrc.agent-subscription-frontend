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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

class BusinessIdentificationControllerWithAssuranceFlagISpec extends BusinessIdentificationControllerISpec {
  override def agentAssuranceRun = true

  override def agentAssurancePayeCheck: Boolean = true

  "submitBusinessDetailsForm with the agentAssuranceFlag set to true" should {
    "redirect to /confirm-business page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      sessionStoreService.currentSession.knownFactsResult shouldBe Some(
        KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(true),
        passSaAgentAssuranceCheck = Some(true),
        passVatDecOrgAgentAssuranceCheck = Some(true),
        passIRCTAgentAssuranceCheck = Some(true))
      metricShouldExistAndBeUpdated("Count-Subscription-ConfirmBusiness-Success")
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(true),
        passSaAgentAssuranceCheck = Some(true),
      passVatDecOrgAgentAssuranceCheck = Some(true),
        passIRCTAgentAssuranceCheck = Some(true))
      metricShouldExistAndBeUpdated("Count-Subscription-ConfirmBusiness-Success")
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "fail when a matching registration is found for the UTR and postcode for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(false),
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))
    }

    "fail when the business registration found by agent-subscription is not already subscribed for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(false),
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed " +
      "for an agent without an acceptable number of PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "proceed to showConfirmBusiness when there is not an acceptable number of PAYE, HMCE-VATDEC-ORG and IR-CT clients, but there is enough SA Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(false),
        passSaAgentAssuranceCheck = Some(true),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))
    }

    "proceed to showConfirmBusiness when there in not an acceptable number of SA, HMCE-VATDEC-ORG andIR-CT clients, but there is enough PAYE Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(true),
        passSaAgentAssuranceCheck = Some(false),
      passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))
    }

    "proceed to showConfirmBusiness when there in not an acceptable number of SA, IR-CT and PAYE client, but there is not enough HMCE-VATDEC-ORG Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(true),
        passSaAgentAssuranceCheck = Some(true),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(true))
    }

    "proceed to showConfirmBusiness when there is an acceptable number of HMCE-VATDEC-ORG client, but there is not enough SA, IR-CT and PAYE clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(false),
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(true),
        passIRCTAgentAssuranceCheck = Some(true))
    }

    "proceed to showConfirmBusiness when there in not an acceptable number of SA, PAYE, HMCE-VATDEC-ORG clients, but there is not enough IR-CT Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(true),
        passSaAgentAssuranceCheck = Some(true),
        passVatDecOrgAgentAssuranceCheck = Some(true),
        passIRCTAgentAssuranceCheck = Some(false))
    }

    "proceed to showConfirmBusiness when there in not an acceptable number of SA, PAYE, HMCE-VATDEC-ORG clients, but there is enough IR-CT Clients" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = Some(false),
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(true))
    }

    "redirect to /cannot-create account when agent's utr is in the R2DW list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithUtrIsForbidden(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      verify(
        1,
        getRequestedFor(urlPathEqualTo(
          s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}"))
      )
      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(0, validUtr.value)
      verifyCheckForAcceptableNumberOfClients("IR-PAYE", 0)
      verifyCheckForAcceptableNumberOfClients("IR-SA", 0)
      verifyCheckForAcceptableNumberOfClients("HMCE-VATDEC-ORG", 0)
      verifyCheckForAcceptableNumberOfClients("IR-CT", 0)
    }

    "continue checks when UTR is not found the R2DW list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      verify(
        1,
        getRequestedFor(urlPathEqualTo(
          s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}"))
      )
      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verifyCheckForAcceptableNumberOfClients("IR-PAYE", 1)
      verifyCheckForAcceptableNumberOfClients("IR-SA", 1)
      verifyCheckForAcceptableNumberOfClients("HMCE-VATDEC-ORG", 1)
      verifyCheckForAcceptableNumberOfClients("IR-CT", 1)
    }

    "exception received due to missing config in R2DW" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithReturns404(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[IllegalStateException] shouldBe thrownBy(await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request)))

      verify(
        1,
        getRequestedFor(urlPathEqualTo(
          s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}"))
      )
      verifyCheckRefusalToDealWith(1, validUtr.value)
    }

    "proceed direct to showConfirmBusinessForm and skip assurance checks " +
      "when agent's UTR is not in the Refusal to Deal With list but is in the Manually Assured Agents list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verify(
        1,
        getRequestedFor(urlPathEqualTo(
          s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}"))
      )
      verifyCheckForAcceptableNumberOfClients("IR-PAYE", 0)
      verifyCheckForAcceptableNumberOfClients("IR-SA", 0)
      verifyCheckForAcceptableNumberOfClients("HMCE-VATDEC-ORG", 0)
      verifyCheckForAcceptableNumberOfClients("IR-CT", 0)
      verifyAgentAssuranceAuditRequestSent(passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = None, passVatDecOrgAgentAssuranceCheck = None, passIRCTAgentAssuranceCheck = None)
    }

    "perform all usual assurance checks when agent's UTR is not in the Manually Assured Agents list" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyCheckRefusalToDealWith(1, validUtr.value)
      verifyCheckAgentIsManuallyAssured(1, validUtr.value)
      verify(
        1,
        getRequestedFor(urlPathEqualTo(
          s"/agent-subscription/registration/${encodePathSegment(validUtr.value)}/postcode/${encodePathSegment(validPostcode)}"))
      )
      verifyCheckForAcceptableNumberOfClients("IR-PAYE", 1)
      verifyCheckForAcceptableNumberOfClients("IR-SA", 1)
      verifyCheckForAcceptableNumberOfClients("HMCE-VATDEC-ORG", 1)
      verifyCheckForAcceptableNumberOfClients("IR-CT", 1)
    }
  }
}