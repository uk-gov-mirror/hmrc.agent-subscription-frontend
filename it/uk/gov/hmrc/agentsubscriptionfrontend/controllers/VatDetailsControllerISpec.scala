package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.DateOfBirth
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

class VatDetailsControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: VatDetailsController = app.injector.instanceOf[VatDetailsController]

  "GET /registered-for-vat" should {
    "display the page with expected content" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "yes")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.showRegisteredForVatForm()(request))

      result should containMessages("registered-for-vat.title", "registered-for-vat.option.yes", "registered-for-vat.option.no")
    }
  }

  "POST /registered-for-vat" should {
    "handle valid form and redirect when choice is yes" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "yes")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))

      val result = await(controller.submitRegisteredForVatForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showVatDeatilsForm().url)
    }

    "handle valid form and redirect when choice is no" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "no")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))

      val result = await(controller.submitRegisteredForVatForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
    }

    "handle form with errors" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))
      val result = await(controller.submitRegisteredForVatForm()(request))

      result should containMessages(
        "registered-for-vat.title",
        "registered-for-vat.option.yes",
        "registered-for-vat.option.no",
        "registered-for-vat.error.no-radio-selected")
    }
  }
}
