package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{charset, contentType, redirectLocation}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD

class TaskListControllerISpec extends BaseISpec {
  lazy val controller: TaskListController = app.injector.instanceOf[TaskListController]

  "showTaskList (GET /task-list)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTaskList(_))

    "contain page titles and header content" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTaskList(request))

      result should containMessages(
        "task-list.header",
        "task-list.1.header",
        "task-list.2.header",
        "task-list.3.header",
        "task-list.4.header")
    }
    "contain CONTINUE tag when a task has been completed" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(taskListFlags = TaskListFlags(businessTaskComplete = true)))

      val result = await(controller.showTaskList(request))
      result should containMessages(
        "task-list.header",
        "task-list.completed")
    }
    "contain a CONTINUE tag when amls task has been completed and allow agent to re-click link when they are not manually assured" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(taskListFlags = TaskListFlags(businessTaskComplete = true, amlsTaskComplete = true)))

      val result = await(controller.showTaskList(request))
      result should containMessages(
        "task-list.header", "task-list.1.amls",
        "task-list.completed")

      checkHtmlResultWithBodyText(result,
        "<a href=/agent-subscription/check-money-laundering-compliance>Enter your money laundering compliance details</a>")
    }
    "block link to complete amls task when user is manually assured" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(taskListFlags =
        TaskListFlags(businessTaskComplete = true, amlsTaskComplete = true, isMAA = true)))

      val result = await(controller.showTaskList(request))

      checkHtmlResultWithNotBodyText(result,
        "<a href=/agent-subscription/check-money-laundering-compliance>Enter your money laundering compliance details</a>")

    }
    "redirect to start if there is a continue url in the request" in {
      val sessionKeys = userIsAuthenticated(subscribingAgentEnrolledForNonMTD)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", "/agent-subscription/task-list?continue=/some/url").withSession(sessionKeys: _*)

      val result = await(controller.showTaskList(request))
      status(result) shouldBe 303
      redirectLocation(result)(defaultTimeout) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }


}
