package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.withMatchingUtrAndPostcode
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

class BusinessIdentificationControllerPayeCheckISpec extends BaseISpec  {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> true,
        "features.agent-assurance-paye-check" -> false,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "submitBusinessDetailsForm with the agentAssuranceFlag set to true and agentAssurancePayeCheck to false" should {
    "redirect to confirm business page and store known facts result in the session store when a matching registration is found for the UTR and postcode" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(validBusinessTypes.head)))

      val result = await(controller.submitBusinessDetailsForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
      sessionStoreService.currentSession.knownFactsResult shouldBe Some(
        KnownFactsResult(validUtr, validPostcode, "My Agency", isSubscribedToAgentServices = false, Some(businessAddress), Some("someone@example.com")))
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(true),
        passVatDecOrgAgentAssuranceCheck = Some(true),
        passIRCTAgentAssuranceCheck = Some(true))
      metricShouldExistAndBeUpdated("Count-Subscription-ConfirmBusiness-Success")
    }

    "fail when a matching registration is found for the UTR and postcode for an agent when failing the SaAgent check" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(validBusinessTypes.head)))

      val result = await(controller.submitBusinessDetailsForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))
    }
  }
}