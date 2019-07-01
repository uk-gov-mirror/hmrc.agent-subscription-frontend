package uk.gov.hmrc.agentsubscriptionfrontend.auth

import com.kenshoo.play.metrics.Metrics
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContinueUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForHMRCASAGENT, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.Future

class AuthActionsSpec extends BaseISpec with MockitoSugar with BeforeAndAfterEach {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  object TestController extends AuthActions {

    implicit val hc = HeaderCarrier()
    implicit val request = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")
    import scala.concurrent.ExecutionContext.Implicits.global

    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    override def appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    override def continueUrlActions: ContinueUrlActions = app.injector.instanceOf[ContinueUrlActions]
    override def metrics: Metrics = app.injector.instanceOf[Metrics]

    def withSubscribedAgent[A]: Result =
      await(super.withSubscribedAgent { arn => Future.successful(Ok(arn.value)) })

    def withSubscribingOrSubscribedAgent[A]: Result = await(TestController.withSubscribingOrSubscribedAgent(
      _ => Future successful Ok("unsubscribed"))(Future successful Ok("subscribed")))

    def storeCheckAnswersComplete =
      sessionStoreService.cacheAgentSession(AgentSession(taskListFlags = TaskListFlags(checkAnswersComplete = true)))

  }

  "withSubscribedAgent" should {
    "call body with arn when valid agent" in {
      authenticatedAgent("fooArn")

      val result = TestController.withSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "fooArn"
    }

    "throw InsufficientEnrolments when agent not enrolled for service" in {
      givenAuthorisedFor(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-MTD-IT", "identifiers": [
           |    { "key":"MTDITID", "value": "fooMtdItId" }
           |  ]}
           |]}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withSubscribedAgent
      }
    }

    "throw InsufficientEnrolments when expected agent's identifier missing" in {
      givenAuthorisedFor(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-AS-AGENT", "identifiers": [
           |    { "key":"BAR", "value": "fooArn" }
           |  ]}
           |]}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withSubscribedAgent
      }
    }

    "UnsupportedAuthProvider error should redirect user to start page" in {
      userLoggedInViaUnsupportedAuthProvider()
      val result = TestController.withSubscribedAgent

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/agent-subscription/finish-sign-out")
    }
  }

  "withSubscribingOrSubscribedAgent" should {
    "call body with a valid unsubscribed agent" in {
      authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      val result = TestController.withSubscribingOrSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "unsubscribed"
    }
    "return the taskListSubscribed result when there is a check answers complete true flag in the session" in {
      authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
      TestController.storeCheckAnswersComplete
      val result = TestController.withSubscribingOrSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "subscribed"
    }
  }
}
