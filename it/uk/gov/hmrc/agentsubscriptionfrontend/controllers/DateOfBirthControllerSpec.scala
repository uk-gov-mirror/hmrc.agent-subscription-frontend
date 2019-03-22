package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, DateOfBirth}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class DateOfBirthControllerSpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: DateOfBirthController = app.injector.instanceOf[DateOfBirthController]

  "GET /date-of-birth page" should {

    "display the page with expected content" in {

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))
      val result = await(controller.showDateOfBirthForm()(request))

      result should containMessages("date-of-birth.title", "date-of-birth.hint")
    }

    "pre-populate the date-of-birth if one is already stored in the session" in {
      val dob = LocalDate.of(2010, 1, 1)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), dateOfBirth = Some(DateOfBirth(dob)))))

      val result = await(controller.showDateOfBirthForm()(request))

      val doc = Jsoup.parse(bodyOf(result))

      var link = doc.getElementById("dob.day")
      link.attr("value") shouldBe "1"

      link = doc.getElementById("dob.month")
      link.attr("value") shouldBe "1"

      link = doc.getElementById("dob.year")
      link.attr("value") shouldBe "2010"
    }
  }

  "POST /date-of-birth form" should {
    "read the dob as expected and save it to the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1950")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.VatDetailsController.showRegisteredForVatForm().url)

      val dob = DateOfBirth(LocalDate.of(1950, 1, 1))

      sessionStoreService.currentSession.agentSession shouldBe Some(agentSession.copy(dateOfBirth = Some(dob)))
    }

    "handle forms with date-of-birth in future" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "2030")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.must.be.past")
    }

    "handle forms with date-of-birth earlier than 1900" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "01", "dob.month" -> "01", "dob.year" -> "1899")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.must.be.later.than.1900")
    }

    "handle forms with date-of-birth fields as non-digits" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "xx", "dob.month" -> "11", "dob.year" -> "2010")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.invalid")
    }

    "display consolidated error when month and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.day" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.month-year.empty")
    }

    "display consolidated error when day and year are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.month" -> "1")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-year.empty")
    }

    "display consolidated error when day and month are empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("dob.year" -> "1980")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitDateOfBirthForm()(request))

      status(result) shouldBe 200
      result should containMessages("date-of-birth.title", "date-of-birth.hint", "date-of-birth.day-month.empty")
    }
  }

}
