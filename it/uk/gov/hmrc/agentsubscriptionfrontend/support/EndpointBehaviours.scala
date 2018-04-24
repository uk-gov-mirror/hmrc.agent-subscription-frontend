package uk.gov.hmrc.agentsubscriptionfrontend.support

import akka.stream.Materializer
import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.individual
import uk.gov.hmrc.play.test.UnitSpec

trait EndpointBehaviours {
  me: UnitSpec with WireMockSupport with OneAppPerSuite with MetricTestSupport =>
  type PlayRequest = Request[AnyContent] => Result

  private implicit val materializer: Materializer = app.materializer

  protected def authenticatedAs(user: SampleUser): FakeRequest[AnyContentAsEmpty.type]

  protected def anAgentAffinityGroupOnlyEndpoint(doRequest: PlayRequest): Unit = {
    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()

      val request = FakeRequest()
      val result = await(doRequest(request))

      status(result) shouldBe 303
      redirectLocation(result).get should include("/gg/sign-in")
      noMetricExpectedAtThisPoint()
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsNotAnAgent(individual)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = await(doRequest(request))

      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.StartController.showNonAgentNextSteps().url
      metricShouldExistsAndBeenUpdated("Count-Subscription-NonAgent")
    }
  }

  protected def aPageWithFeedbackLinks(action: PlayRequest, request: => Request[AnyContent] = FakeRequest()): Unit = {

    "have a 'get help with this page' link" in {
      val result = await(action(request))

      bodyOf(result) should include("Get help with this page.")
    }

    "have a beta feedback banner" in {
      val result = await(action(request))

      bodyOf(result) should include("This is a new service")
    }

    "have a beta feedback link" in {
      val result = await(action(request))

      bodyOf(result) should include("/contact/beta-feedback")
    }
  }
}
