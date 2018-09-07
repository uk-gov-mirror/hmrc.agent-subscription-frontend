package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD

class BusinessIdentificationControllerWithoutAssuranceFlagISpec extends BusinessIdentificationControllerISpec {

  override val agentAssuranceRun: Boolean = false
  override def agentAssurancePayeCheck: Boolean = true

  "submitBusinessDetailsForm with the agentAssuranceFlag set to false" should {
    "redirect to confirm business page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationForms.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      sessionStoreService.currentSession.knownFactsResult shouldBe
        Some(KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "store isSubscribedToAgentServices = false in session when the business registration found by agent-subscription is not already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationForms.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      sessionStoreService.currentSession.knownFactsResult.get.isSubscribedToAgentServices shouldBe false
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.submitBusinessDetailsForm(Some(BusinessIdentificationForms.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
      verifyAuditRequestNotSent(AgentSubscriptionFrontendEvent.AgentAssurance)
    }
  }
}