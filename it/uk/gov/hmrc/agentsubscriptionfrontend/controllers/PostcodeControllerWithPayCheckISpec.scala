package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestSetupNoJourneyRecord

import scala.concurrent.ExecutionContext.Implicits.global

class PostcodeControllerWithPayCheckISpec extends BaseISpec with SessionDataMissingSpec {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> true,
        "features.agent-assurance-paye-check" -> false,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "submitPostcodeForm" should {

    "read the form and redirect to /national-insurance-number if businessType is SoleTrader or Partnership" in new TestSetupNoJourneyRecord {
      List(SoleTrader, Partnership).foreach { businessType =>
        withMatchingUtrAndPostcode(validUtr, validPostcode)
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
        givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
        givenAgentIsNotManuallyAssured(validUtr.value)

        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = Some(Postcode(validPostcode)), nino = None))
      }
    }

    "read the form and redirect to /company-registration-number if businessType is Limited Company or Llp" in new TestSetupNoJourneyRecord{
      List(LimitedCompany, Llp).foreach { businessType =>
        withMatchingUtrAndPostcode(validUtr, validPostcode)
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
        givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
        givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
        givenAgentIsNotManuallyAssured(validUtr.value)

        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = Some(Postcode(validPostcode)), nino = None))
      }
    }

    "fail when a matching registration is found for the UTR and postcode for an agent when failing the SaAgent check" in new TestSetupNoJourneyRecord{
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AssuranceChecksController.invasiveCheckStart().url)
      verifyAgentAssuranceAuditRequestSent(
        passPayeAgentAssuranceCheck = None,
        passSaAgentAssuranceCheck = Some(false),
        passVatDecOrgAgentAssuranceCheck = Some(false),
        passIRCTAgentAssuranceCheck = Some(false))

      sessionStoreService.fetchAgentSession.get.registration.get.taxpayerName shouldBe Some(registrationName)
    }

    "redirect to /business-type if businessType is not found in session" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "handle for with invalid postcodes" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "sdsds")
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 200

      result should containMessages(
        "postcode.title",
        "error.postcode.invalid"
      )
    }
  }
}
