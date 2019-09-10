package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.agentSession
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global

class NationalInsuranceControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: NationalInsuranceController = app.injector.instanceOf[NationalInsuranceController]

  "show /national-insurance-number form" should {
    "display the form as expected" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.SoleTrader))))

      val result = await(controller.showNationalInsuranceNumberForm()(request))

      result should containMessages(
        "nino.title",
        "nino.hint"
      )
    }

    "pre-populate the NINO if one is already stored in the session" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), Some(Utr("abcd")), nino = Some(Nino("AE123456C")))))

      val result = await(controller.showNationalInsuranceNumberForm()(request))

      result should containInputElement("nino", "text", Some("AE123456C"))
    }
  }

  "submit /national-insurance-number form" should {
    "read the form and redirect to /date-of-birth page" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("nino" -> "AE123456C")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitNationalInsuranceNumberForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.DateOfBirthController.showDateOfBirthForm().url)
    }

    "handle forms with invalid nino" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("nino" -> "AE123456C_BLAH")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.submitNationalInsuranceNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages(
        "nino.title",
        "nino.hint",
        "error.nino.invalid"
      )
    }
  }

}
