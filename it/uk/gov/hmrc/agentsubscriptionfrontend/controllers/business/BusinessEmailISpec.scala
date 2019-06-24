package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{validUtr, _}
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessEmailISpec extends BaseISpec {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessEmailForm(request))

    "display business email form" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

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

    "display business email form when email address in initial details is empty" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration.copy(emailAddress = None))))

      val result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "businessEmail.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe ""

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessEmailForm(request))

    "update business email after submission, redirect to AMLS when there is a continue url" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))
      sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/continue/url"))

      val result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.AMLSController.showCheckAmlsPage().url

      await(sessionStoreService.fetchAgentSession).get.registration.get.emailAddress shouldBe Some("newagent@example.com")
    }

    "update business email after submission and redirect to /check-answers if user is changing answers" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("email" -> "newagent@example.com")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))
      sessionStoreService.currentSession.changingAnswers = Some(true)

      val result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      await(sessionStoreService.fetchIsChangingAnswers) shouldBe Some(true)
    }

    "show validation error when the form is submitted with empty email" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("email" -> "")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitBusinessEmailForm(request))

      result should containMessages("businessEmail.title", "error.business-email.empty")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

}
