package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD

class CheckAgencyControllerPayeCheckISpec extends CheckAgencyControllerISpec {
  override def agentAssuranceRun: Boolean = true
  override def agentAssurancePayeCheck: Boolean = false

  "checkAgencyStatus with the agentAssuranceFlag set to true and agentAssurancePayeCheck to false" should {
    "redirect to confirm agency page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(Some(CheckAgencyController.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
      sessionStoreService.currentSession.knownFactsResult shouldBe Some(
        KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false))
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(true))
      metricShouldExistsAndBeenUpdated("Count-Subscription-CheckAgency-Success")
    }

    "fail when a matching registration is found for the UTR and postcode for an agent when failing the SaAgent check" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result = await(controller.checkAgencyStatus(Some(CheckAgencyController.validBusinessTypes.head))(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(false))
    }
  }
}