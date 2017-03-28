package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub

class StartControllerISpec extends BaseControllerISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]

  "context root" should {
    "redirect to start page" in {
      val result = await(controller.root(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/start")
    }
  }

  "start" should {
    "not require authentication" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 200
    }

    "be available" in {
      val result = await(controller.start(FakeRequest()))

      bodyOf(result) should include("Subscribe to new agent services")
    }

    behave like aPageWithFeedbackLinks(request => controller.start(request))

  }

  "showNonAgentNextSteps" should {
    "display the non-agent next steps page if the current user is logged in" in {
      val request = authenticatedRequest()
      val result = await(controller.showNonAgentNextSteps(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Affinity Group")
    }

    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()

      val request = FakeRequest()
      val result = await(controller.showNonAgentNextSteps(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
    }

    behave like aPageWithFeedbackLinks(request => controller.showNonAgentNextSteps(request), authenticatedRequest())
  }

}
