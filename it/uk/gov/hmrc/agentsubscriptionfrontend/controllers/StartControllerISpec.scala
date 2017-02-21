package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class StartControllerISpec extends BaseControllerISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]

  "showNonAgentNextSteps" should {
    "display the non-agent next steps page if the current user is logged in" in {
      val sessionKeys = AuthStub.userIsAuthenticated(individual)

      val request = FakeRequest().withSession(sessionKeys: _*)
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
  }

}
