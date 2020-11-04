package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, CompanyRegistrationNumber}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

class CompanyRegistrationControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: CompanyRegistrationController = app.injector.instanceOf[CompanyRegistrationController]

  "/GET company-registration-number" should {

    "display the page with expected content" in new TestSetupNoJourneyRecord {

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showCompanyRegNumberForm()(request))

      result should containMessages("crn.title", "crn.hint")
    }

    "pre-populate the crn if one is already stored in the session" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), companyRegistrationNumber = Some(CompanyRegistrationNumber("12345")))))

      val result = await(controller.showCompanyRegNumberForm()(request))

      result should containInputElement("crn", "text", Some("12345"))
    }
  }

  "POST /company-registration-number" should {

    "read the crn as expected and save it to the session when supplied utr matches with DES utr (retrieved using crn)" in new TestSetupNoJourneyRecord{
      val crn = CompanyRegistrationNumber("12345678")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "12345678")

      AgentSubscriptionStub.withMatchingCtUtrAndCrn(agentSessionForLimitedCompany.utr.get, crn)

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSessionForLimitedCompany.copy(companyRegistrationNumber = Some(crn), nino = None, ctUtrCheckResult = Some(true)))
    }


    "redirect to /no-match page when supplied utr does not matches with DES utr (retrieved using crn)" +
      "when businessType is NOT LLP" in new TestSetupNoJourneyRecord{
      val crn = CompanyRegistrationNumber("12345678")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "12345678")

      AgentSubscriptionStub.withNonMatchingCtUtrAndCrn(agentSessionForLimitedCompany.utr.get, crn)

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSessionForLimitedCompany.copy(companyRegistrationNumber = None))
    }

    "redirect to /interrupt page when supplied utr does not matches with DES utr (retrieved using crn) and" +
      "businessType is Llp" in new TestSetupNoJourneyRecord{
      val crn = CompanyRegistrationNumber("12345678")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "12345678")

      AgentSubscriptionStub.withNonMatchingCtUtrAndCrn(agentSessionForLimitedPartnership.utr.get, crn)

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedPartnership)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showLlpInterrupt().url)

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSessionForLimitedPartnership
        .copy(companyRegistrationNumber = Some(CompanyRegistrationNumber("12345678")), ctUtrCheckResult = Some(false)))
    }

    "redirect to /unique-taxpayer-reference page utr is not available in agent session" in new TestSetupNoJourneyRecord{

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "12345678")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany.copy(utr = None))

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.UtrController.showUtrForm().url)
    }

    "handle forms with empty field" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.empty")
    }

    "handle forms with a crn that's too short" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "1234567")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }

    "handle forms with a crn that's too long" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "123456789")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }

    "handle forms with an invalid crn" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "1AA23456")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }
  }

  "GET /interrupt" should {

    "display the page with expected content" in new TestSetupNoJourneyRecord {

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(Llp))))
      val result = await(controller.showLlpInterrupt()(request))

      result should containMessages("llp-interrupt.title", "llp-interrupt.p1", "llp-interrupt.p2")

      result should containLink("button.back", routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

      result should containLink("button.continue", routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "redirect to /business-type if businessType is not LLP" in new TestSetupNoJourneyRecord {

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showLlpInterrupt()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }
}
