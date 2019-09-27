package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

class PostcodeControllerWithAssuranceFlagISpec extends BaseISpec with SessionDataMissingSpec {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> true,
        "features.agent-assurance-paye-check" -> true,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "GET /postcode" should {
    "display the postcode page" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))
      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "What is the postcode of your registered address?")
    }
  }

  "POST /postcode" when {

    def stubs = {
      withMatchingUtrAndPostcode(validUtr, validPostcode)
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsAnAgentWithAnAcceptableNumberOfClients("IR-CT")
      givenRefusalToDealWithUtrIsNotForbidden(validUtr.value)
      givenAgentIsNotManuallyAssured(validUtr.value)
    }

    "businessType is SoleTrader or Partnership" should {

      "redirect to /national-insurance-number page if nino exists" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          stubs
          implicit val request =
            authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C"))).withFormUrlEncodedBody("postcode" -> validPostcode)

          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

          sessionStoreService.currentSession.agentSession shouldBe
            Some(
              agentSession.copy(
                businessType = Some(businessType),
                postcode = Some(Postcode(validPostcode)),
                nino = None,
                registration = Some(testRegistration.copy(emailAddress = Some("someone@example.com")))))
        }
      }

      "redirect to /registered-for-vat page if nino doesn't exist" in new TestSetupNoJourneyRecord {
        List(SoleTrader, Partnership).foreach { businessType =>
          stubs
          implicit val request =
            authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)

          sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

          sessionStoreService.currentSession.agentSession shouldBe
            Some(
              agentSession.copy(
                businessType = Some(businessType),
                postcode = Some(Postcode(validPostcode)),
                nino = None,
                registration = Some(testRegistration.copy(emailAddress = Some("someone@example.com")))))
        }
      }
    }

    "businessType is Limited Company or Llp" should {

      "redirect to /company-registration-number" in new TestSetupNoJourneyRecord {
        List(LimitedCompany, Llp).foreach { businessType =>
         stubs
          implicit val request =
            authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
          sessionStoreService.currentSession.agentSession =
            Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

          val result = await(controller.submitPostcodeForm()(request))

          status(result) shouldBe 303

          redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

          sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(
            testRegistration.copy(emailAddress = Some("someone@example.com")))
        }
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

    "redirect to /business-type if businessType is not found in session" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "redirect to /utr if there is no utr in the session" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")
      sessionStoreService.currentSession.agentSession =
        Some(agentSession.copy(postcode = None, nino = None, utr = None))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.UtrController.showUtrForm().url)
    }

    "redirect to cannot create account if user is on the refusal to deal with list" in new TestSetupNoJourneyRecord {
      givenRefusalToDealWithUtrIsForbidden(validUtr.value)
      withMatchingUtrAndPostcode(validUtr, validPostcode)

      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
    }

    "handle for with invalid postcodes" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "sdsds")
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 200

      result should containMessages(
        "postcode.sole_trader.title",
        "error.postcode.invalid"
      )
    }
  }
}
