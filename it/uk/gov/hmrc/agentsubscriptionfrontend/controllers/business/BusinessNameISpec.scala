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
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent

class BusinessNameISpec extends BaseISpec {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display business name form if the name is des complaint" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.showBusinessNameForm(request))
      result should containMessages("businessName.title", "businessName.description", "businessName.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("name").`val` shouldBe "My Agency"

      val backLink = doc.getElementsByClass("link-back")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "display business name form if the name is not des complaint" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration.copy(taxpayerName = Some("My Agency &")))))

      val result = await(controller.showBusinessNameForm(request))
      result should containMessages(
        "businessName.updated.title",
        "businessName.updated.p1",
        "businessName.description",
        "businessName.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("name").`val` shouldBe "My Agency &"

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business name after submission, redirect to task list when there is a continue url" in new TestSetupNoJourneyRecord {
      val agentSession = AgentSession(
        Some(BusinessType.SoleTrader),
        utr = Some(validUtr),
        registration = Some(testRegistration),
        postcode = Some(Postcode("AA1 1AA")))
      val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, AuthProviderId("12345-credId"))
      val newSjr = sjr.copy(
        continueId = None,
        businessDetails = sjr.businessDetails.copy(
          registration = Some(testRegistration.copy(taxpayerName = Some("new Agent name")))
        )
      )
      givenSubscriptionRecordCreated(AuthProviderId("12345-credId"), newSjr)
      givenAgentIsNotManuallyAssured(validUtr.value)
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "new Agent name")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/continue/url"))

      val result = await(controller.submitBusinessNameForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url

      await(sessionStoreService.currentSession).agentSession.get.registration.get.taxpayerName shouldBe Some(
        "new Agent name")
    }

    "update business name after submission and redirect to /check-answers if user is changing answers" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "new Agent name")
      sessionStoreService.currentSession.changingAnswers = Some(true)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitBusinessNameForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "show validation error when the form is submitted with empty name" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.empty")
    }

    "show validation error when the form is submitted with non des complaint name after check-answers page" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))
      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.invalid")
    }

    "show validation error when the form is submitted with non des complaint name" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.agentSession = Some(
        AgentSession(
          Some(BusinessType.SoleTrader),
          utr = Some(validUtr),
          registration = Some(testRegistration.copy(taxpayerName = Some("Some name *")))))

      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.updated.title", "error.business-name.invalid")
    }
  }

}
