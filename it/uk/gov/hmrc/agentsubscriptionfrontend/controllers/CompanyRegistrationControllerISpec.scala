package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.CompanyRegistrationNumber
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

class CompanyRegistrationControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: CompanyRegistrationController = app.injector.instanceOf[CompanyRegistrationController]

  "/GET company-registration-number" should {

    "display the page with expected content" in {

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCompanyRegNumberForm()(request))

      result should containMessages("crn.title", "crn.hint")
    }
  }

  "POST /company-registration-number" should {

    "read the crn as expected and save it to the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "12345678")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      val crn = CompanyRegistrationNumber("12345678")

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSessionForLimitedCompany.copy(companyRegistrationNumber = Some(crn)))
    }

    "handle forms with empty field" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.empty")
    }

    "handle forms with a crn that's too short" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "1234567")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }

    "handle forms with a crn that's too long" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "123456789")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }

    "handle forms with an invalid crn" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("crn" -> "1AA23456")

      sessionStoreService.currentSession.agentSession = Some(agentSessionForLimitedCompany)

      val result = await(controller.submitCompanyRegNumberForm()(request))

      status(result) shouldBe 200

      result should containMessages("error.crn.invalid")
    }
  }
}
