package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub

class StartControllerISpec extends BaseControllerISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]
  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("government-gateway.url" -> configuredGovernmentGatewayUrl)

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

      bodyOf(result) should include("Create your Agent Services account")
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
      bodyOf(result) should include(htmlEscapedMessage("This isn't an agent account"))
    }

    "allow the government gateway URL to be configured" in {
      val result = await(controller.showNonAgentNextSteps(authenticatedRequest()))

      status(result) shouldBe 200
      bodyOf(result) should include("/signed-out")
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

  "returnAfterGGCredsCreated" should {
    "redirect to check-agency-status page" in {
      val result = await(controller.returnAfterGGCredsCreated(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/check-agency-status")
    }
  }
}
