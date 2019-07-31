package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import java.time.LocalDate

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, AuthProviderId, RegisteredDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForHMRCASAGENT, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}

class TaskListControllerISpec extends BaseISpec {
  lazy val controller: TaskListController = app.injector.instanceOf[TaskListController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  "showTaskList (GET /task-list)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTaskList(_))

    "contain page titles and header content when the user is not subscribed" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      result should containMessages(
        "task-list.header",
        "task-list.1.header",
        "task-list.2.header",
        "task-list.3.header",
        "task-list.4.header")
    }
    "contain page titles and header content when the user is subscribed" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))

      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

      val result = await(controller.showTaskList(request))

      result should containMessages(
        "task-list.header",
        "task-list.1.header",
        "task-list.2.header",
        "task-list.3.header",
        "task-list.4.header")
    }
    "contain CONTINUE tag when a task has been completed" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(amlsData = Some(AmlsData(
            amlsRegistered = true,
            amlsAppliedFor = Some(false),
            amlsDetails =
              Some(AmlsDetails("supervisory body", Right(RegisteredDetails("123", LocalDate.now().plusDays(10)))))
          )))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.completed")
    }

    "contain a CONTINUE tag when amls task has been completed and allow agent to re-click link when they are not manually assured" in {

      givenAgentIsNotManuallyAssured(validUtr.value)

      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData
          .minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId"))
          .copy(
            amlsData = Some(
              AmlsData(
                amlsRegistered = true,
                amlsAppliedFor = Some(false),
                amlsDetails =
                  Some(AmlsDetails("supervisory body", Right(RegisteredDetails("123", LocalDate.now().plusDays(10)))))
              )))
      )

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      result should containMessages("task-list.header", "task-list.1.amls", "task-list.completed")

      checkHtmlResultWithBodyText(
        result,
        "<a href=/agent-subscription/check-money-laundering-compliance>Enter your money laundering compliance details</a>")
    }

    "contain a url to the mapping journey when user has completed all other tasks" in {
      givenAgentIsNotManuallyAssured(validUtr.value)
      givenSubscriptionJourneyRecordExists(
        AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")).copy(subscriptionCreated = true))

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 200

      checkHtmlResultWithBodyText(result, "/agent-subscription/begin-mapping")
    }
  }

  "savedProgress (GET /saved-progress)" should {
    "contain page title and content" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result = await(controller.savedProgress(backLink = None)(request))

      status(result) shouldBe 200

      result should containMessages(
        "saved-progress.title",
        "saved-progress.p1",
        "saved-progress.p2",
        "saved-progress.link",
        "saved-progress.continue"
      )

      result should containSubstrings(
        "To complete this form later, go to the",
        "guidance page about creating an agent services account (open in a new window or tab)",
        "on GOV.UK and sign in to this service again."
      )

      result should containLink("saved-progress.continue", routes.TaskListController.showTaskList().url)
      result should containLink("saved-progress.finish", routes.SignedOutController.signOut().url)
    }
  }
}
