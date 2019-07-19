package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId, BusinessType, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{validUtr, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.givenNoSubscriptionJourneyRecordExists
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessEmailISpec extends BaseISpec {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessEmailForm(request))

    "display business email form" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "businessEmail.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe "test@gmail.com"

      val backLink = doc.getElementsByClass("link-back")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "display business email form when email address in initial details is empty" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration.copy(emailAddress = None))))

      val result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "businessEmail.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe ""

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessEmailForm(request))

    "update business email after submission, redirect to task list when there is a continue url" in new TestSetupNoJourneyRecord {
      val agentSession =
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration),
          postcode = Some(Postcode("AA11AA")))
      givenAgentIsNotManuallyAssured(validUtr.value)

      val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
      val newsjr = sjr.copy(
        businessDetails = sjr.businessDetails.copy(
          registration = Some(testRegistration.copy(emailAddress = Some("newagent@example.com")))))

      givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), newsjr)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
        "email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/continue/url"))

      val result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url

      await(sessionStoreService.fetchAgentSession).get.registration.get.emailAddress shouldBe Some(
        "newagent@example.com")
    }

    "update business email after submission and redirect to /check-answers if user is changing answers" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
        "email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      sessionStoreService.currentSession.changingAnswers = Some(true)

      val result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      await(sessionStoreService.fetchIsChangingAnswers) shouldBe Some(true)
    }

    "show validation error when the form is submitted with empty email" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("email" -> "")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitBusinessEmailForm(request))

      result should containMessages("businessEmail.title", "error.business-email.empty")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

}
