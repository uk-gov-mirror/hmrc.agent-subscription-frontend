package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, CompanyRegistrationNumber, DateOfBirth}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global

class DateOfBirthControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: DateOfBirthController = app.injector.instanceOf[DateOfBirthController]

  "GET /date-of-birth page" should {

    "redirect to /registered-for-vat if nino doesn't exist from auth" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)
    }

    "redirect to /national-insurance-number if there is no authNino and no session nino and the businessType is LLP" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(Llp))))
      val result = await(controller.showDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "redirect to /national-insurance-number page if nino exists from auth but user hasn't assured it yet" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(Llp))))
      val result = await(controller.showDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "redirect to /registered-for-vat when user has assured their nino but No DOB exists in citizen-details" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")))))
      val result = await(controller.showDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)
    }

    "display the page with expected content when dob exists in citizen-details" in new TestSetupNoJourneyRecord {

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")), dateOfBirthFromCid = Some(DateOfBirth(LocalDate.now())))))
      val result = await(controller.showDateOfBirthForm()(request))

      result should containMessages("date-of-birth.title", "date-of-birth.hint")
    }

    "pre-populate the date-of-birth if one is already stored in the session" in new TestSetupNoJourneyRecord{
      val dob = LocalDate.of(2010, 1, 1)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
      await(sessionStoreService.cacheAgentSession(AgentSession(businessType =  Some(SoleTrader), nino = Some(Nino("AE123456C")), dateOfBirthFromCid = Some(DateOfBirth(LocalDate.now())), dateOfBirth = Some(DateOfBirth(dob)))))

      val result = await(controller.showDateOfBirthForm()(request))

      result should containInputElement("dob.day", "tel", Some("1"))
      result should containInputElement("dob.month", "tel", Some("1"))
      result should containInputElement("dob.year", "tel", Some("2010"))
    }
  }

  "POST /date-of-birth form" should {
    "read the dob as expected and save it to the session" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Matchmaker"), dob)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(nino = Some(Nino("AE123456C")), dateOfBirthFromCid = Some(dob)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirth) shouldBe Some(dob)
    }


    "read the dob as expected and save it to the session for an LLP" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Ferguson"), dob)
      AgentSubscriptionStub.givenCompaniesHouseNameCheckReturnsStatus(CompanyRegistrationNumber("01234567"), "FERGUSON", 200)
      sessionStoreService.currentSession.agentSession = Some(agentSession
        .copy(businessType= Some(Llp),
          companyRegistrationNumber = Some(CompanyRegistrationNumber("01234567")),
          nino = Some(Nino("AE123456C")),
          dateOfBirthFromCid = Some(dob),
          lastNameFromCid = Some("Ferguson")))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirth) shouldBe Some(dob)
    }

    "redirect to /no-match-found if for an LLP the companies house check fails" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Ferguson"), dob)
      AgentSubscriptionStub.givenCompaniesHouseNameCheckReturnsStatus(CompanyRegistrationNumber("01234567"), "FERGUSON", 404)
      sessionStoreService.currentSession.agentSession = Some(agentSession
        .copy(businessType= Some(Llp),
          companyRegistrationNumber = Some(CompanyRegistrationNumber("01234567")),
          nino = Some(Nino("AE123456C")),
          dateOfBirthFromCid = Some(dob),
          lastNameFromCid = Some("Ferguson")))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)

      sessionStoreService.currentSession.agentSession.flatMap(_.dateOfBirth) shouldBe None
    }
    "show /no-match-found page when dob from citizen details and user entered input do not match" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))
      AgentSubscriptionStub.givenDesignatoryDetailsForNino(Nino("AE123456C"), Some("Matchmaker"), dob)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(nino = Some(Nino("AE123456C")), dateOfBirthFromCid = Some(DateOfBirth(LocalDate.of(1980, 1, 1)))))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)
    }

    "Redirect to enter nino page when the nino is not found in the session" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")

      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), nino = None)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
    }

    "handle forms with date-of-birth in future" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "2030")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.must.be.past")
    }

    "handle forms with date-of-birth earlier than 1900" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1899")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.must.be.later.than.1900")
    }

    "handle forms with date-of-birth fields as non-digits" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "xx", "dob.month" -> "11", "dob.year" -> "2010")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.invalid")
    }

    "display consolidated error when month and year are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.day" -> "1")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.month-year.empty")
    }

    "display consolidated error when day and year are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.month" -> "1")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-year.empty")
    }

    "display consolidated error when day and month are empty" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD.copy(nino = Some("AE123456C")))
        .withFormUrlEncodedBody("dob.year" -> "1980")
      val dobCid = DateOfBirth(LocalDate.of(1950, 1, 1))
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(dateOfBirthFromCid = Some(dobCid)))

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-month.empty")
    }
  }

}
