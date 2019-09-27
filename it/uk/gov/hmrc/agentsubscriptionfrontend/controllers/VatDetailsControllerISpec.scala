package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, DateOfBirth, VatDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global

class VatDetailsControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: VatDetailsController = app.injector.instanceOf[VatDetailsController]

  "GET /registered-for-vat" should {

    "display /registered-for-vat if nino doesn't exist from auth" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showRegisteredForVatForm()(request))

      status(result) shouldBe 200
      result should containMessages("registered-for-vat.title", "registered-for-vat.option.yes", "registered-for-vat.option.no")
    }

    "redirect to /national-insurance-number page if nino exists from auth but user hasn't assured it yet" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showRegisteredForVatForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "display /registered-for-vat when user has assured their nino but No DOB exists in citizen-details" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")))))
      val result = await(controller.showRegisteredForVatForm()(request))

      status(result) shouldBe 200
      result should containMessages("registered-for-vat.title", "registered-for-vat.option.yes", "registered-for-vat.option.no")
    }

    "redirect to /date-of-birth when user has assured their nino and not DOB yet" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      val dob = LocalDate.of(2010, 1, 1)
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")), dateOfBirthFromCid = Some(DateOfBirth(dob)))))
      val result = await(controller.showRegisteredForVatForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.DateOfBirthController.showDateOfBirthForm().url)
    }

    "pre-populate the registeredForVat if one is already stored in the session" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      val dob = LocalDate.of(2010, 1, 1)
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")), registeredForVat = Some("Yes"), dateOfBirthFromCid = Some(DateOfBirth(dob)), dateOfBirth = Some(DateOfBirth(dob)))))

      val result = await(controller.showRegisteredForVatForm()(request))

      val doc = Jsoup.parse(bodyOf(result))

      val link = doc.getElementById("registeredForVat-yes")
      link.attr("checked") shouldBe "checked"
    }
  }

  "POST /registered-for-vat" should {
    "handle valid form and redirect when choice is yes" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "yes")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))

      val result = await(controller.submitRegisteredForVatForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showVatDetailsForm().url)
    }

    "handle valid form and redirect when choice is no" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("registeredForVat" -> "no")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now()))))

      val result = await(controller.submitRegisteredForVatForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
    }

    "handle form with errors" in new TestSetupNoJourneyRecord{
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
    "display the page with expected content" in new TestSetupNoJourneyRecord{
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

    "pre-populate the vatDetails if one is already stored in the session" in new TestSetupNoJourneyRecord{
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

    "handle valid forms, check vat known facts, store data in session and redirect" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "688931961", "regDate.day" -> "1", "regDate.month" -> "11", "regDate.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      AgentSubscriptionStub.withMatchingVrnAndDateOfReg(Vrn("688931961"),  LocalDate.of(2010, 11, 1))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.vatDetails) shouldBe Some(VatDetails(Vrn("688931961"), LocalDate.of(2010, 11, 1)))
    }

    "redirect to /mo-match page if the vat known facts check fails" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "688931961", "regDate.day" -> "1", "regDate.month" -> "11", "regDate.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      AgentSubscriptionStub.withNonMatchingVrnAndDateOfReg(Vrn("688931961"),  LocalDate.of(2010, 11, 1))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.vatDetails) shouldBe None
    }

    "handle forms with missing vrn" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.vrn.required")

    }

    "handle forms containing vrn with invalid text" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("vrn" -> "blah")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.vrn.regex-failure")

    }

    "handle forms with missing vat registration date" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "", "regDate.month" -> "", "regDate.year" -> "")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.required")

    }

    "handle forms with very old vat registration date < 1900" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "1", "regDate.month" -> "1", "regDate.year" -> "1010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.must.be.later.than.1900")

    }

    "handle forms with future dated vat registration" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "1", "regDate.month" -> "1", "regDate.year" -> "3010")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.must.be.in.past")

    }

    "handle forms with vat registration date as non-digits" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("regDate.day" -> "qq", "regDate.month" -> "we", "regDate.year" -> "erd")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))
      status(result) shouldBe 200

      result should containMessages("vat-details.regDate.invalid")

    }

    "display consolidated error when month and year are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.day" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.month-year.empty")
    }

    "display consolidated error when day and year are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.month" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.day-year.empty")
    }

    "display consolidated error when day and month are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("regDate.year" -> "1980")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirth = Some(DateOfBirth(LocalDate.now())), registeredForVat = Some("Yes")))

      val result = await(controller.submitVatDetailsForm()(request))

      status(result) shouldBe 200
      result should containMessages("vat-details.regDate.day-month.empty")
    }
  }
}
