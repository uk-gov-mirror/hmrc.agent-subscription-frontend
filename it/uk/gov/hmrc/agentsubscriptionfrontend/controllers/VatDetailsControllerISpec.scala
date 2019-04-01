package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, DateOfBirth, VatDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class VatDetailsControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: VatDetailsController = app.injector.instanceOf[VatDetailsController]

  "GET /registered-for-vat" should {
    "display the page with expected content" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "yes")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.showRegisteredForVatForm()(request))

      result should containMessages("registered-for-vat.title", "registered-for-vat.option.yes", "registered-for-vat.option.no")
    }

    "pre-populate the registeredForVat if one is already stored in the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), registeredForVat = Some("Yes"))))

      val result = await(controller.showRegisteredForVatForm()(request))

      val doc = Jsoup.parse(bodyOf(result))

      val link = doc.getElementById("registeredForVat-yes")
      link.attr("checked") shouldBe "checked"

    }
  }

  "POST /registered-for-vat" should {
    "handle valid form and redirect when choice is yes" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "yes")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))

      val result = await(controller.submitRegisteredForVatForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showVatDetailsForm().url)
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

  "GET /vat-registration-details" should {
    "display the page with expected content" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.showVatDetailsForm()(request))

      result should containMessages(
        "vat-details.title",
        "vat-details.vrn.title",
        "vat-details.vrn.hint",
        "vat-details.reg-date.title",
        "vat-details.reg-date.hint")
    }

    "pre-populate the vatDetails if one is already stored in the session" in {
      val vrd = LocalDate.of(2010, 1, 1)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), vatDetails = Some(VatDetails(Vrn("688931961"), vrd)))))

      val result = await(controller.showVatDetailsForm()(request))

      val doc = Jsoup.parse(bodyOf(result))

      var link = doc.getElementById("vrn")
      link.attr("value") shouldBe "688931961"

      link = doc.getElementById("regDate.day")
      link.attr("value") shouldBe "1"

      link = doc.getElementById("regDate.month")
      link.attr("value") shouldBe "1"

      link = doc.getElementById("regDate.year")
      link.attr("value") shouldBe "2010"
    }
  }

  "POST /vat-registration-details" should {

    "handle valid forms, check vat known facts, store data in session and redirect" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "688931961", "regDate.day" -> "1", "regDate.month" -> "11", "regDate.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      AgentSubscriptionStub.withMatchingVrnAndDateOfReg(Vrn("688931961"),  LocalDate.of(2010, 11, 1))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.vatDetails) shouldBe Some(VatDetails(Vrn("688931961"), LocalDate.of(2010, 11, 1)))
    }

    "redirect to /mo-match page if the vat known facts check fails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "688931961", "regDate.day" -> "1", "regDate.month" -> "11", "regDate.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      AgentSubscriptionStub.withNonMatchingVrnAndDateOfReg(Vrn("688931961"),  LocalDate.of(2010, 11, 1))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoAgencyFound().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.vatDetails) shouldBe None
    }

    "handle forms with missing vrn" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.vrn.required")

    }

    "handle forms containing vrn with invalid text" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "blah")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.vrn.regex-failure")

    }

    "handle forms with invalid vrn" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "123456789")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.vrn.checksum-failure")

    }

    "handle forms with missing vat registration date" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "", "regDate.month" -> "", "regDate.year" -> "")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.required")

    }

    "handle forms with very old vat registration date < 1900" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "1", "regDate.month" -> "1", "regDate.year" -> "1010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.must.be.later.than.1900")

    }

    "handle forms with future dated vat registration" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "1", "regDate.month" -> "1", "regDate.year" -> "3010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.must.be.in.past")

    }

    "handle forms with vat registration date as non-digits" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "qq", "regDate.month" -> "we", "regDate.year" -> "erd")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.invalid")

    }

    "display consolidated error when month and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.day" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.month-year.empty")
    }

    "display consolidated error when day and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.month" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.day-year.empty")
    }

    "display consolidated error when day and month are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.year" -> "1980")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.day-month.empty")
    }
  }
}
