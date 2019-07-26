package uk.gov.hmrc.agentsubscriptionfrontend.auth

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContinueUrlActions
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForHMRCASAGENT, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.Future

class AuthActionsSpec extends BaseISpec with MockitoSugar {

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  object TestController extends AuthActions {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")
    import scala.concurrent.ExecutionContext.Implicits.global

    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    override def appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    override def continueUrlActions: ContinueUrlActions = app.injector.instanceOf[ContinueUrlActions]
    override def metrics: Metrics = app.injector.instanceOf[Metrics]
    override def subscriptionJourneyService: SubscriptionJourneyService = app.injector.instanceOf[SubscriptionJourneyService]

    def withSubscribedAgent[A]: Result =
      await(super.withSubscribedAgent { (arn, sjr) => Future.successful(Ok(arn.value)) })

    def withSubscribingOrSubscribedAgent[A]: Result = await(TestController.withSubscribingOrSubscribedAgent(
      _ => Future successful Ok("task list")))

  }

  "withSubscribedAgent" should {
    val providerId = AuthProviderId("12345-credId")

    "call body with arn when valid agent" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId,
        TestData.minimalSubscriptionJourneyRecord(providerId))
      authenticatedAgent("fooArn", "12345-credId")

      val result = TestController.withSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "fooArn"
    }

    "throw InsufficientEnrolments when agent not enrolled for service" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId,
        TestData.minimalSubscriptionJourneyRecord(providerId))
      givenAuthorisedFor(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-MTD-IT", "identifiers": [
           |    { "key":"MTDITID", "value": "fooMtdItId" }
           |  ]}
           |],
           |"optionalCredentials": {"providerId": "${providerId.id}", "providerType": "GovernmentGateway"}}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withSubscribedAgent
      }
    }

    "throw InsufficientEnrolments when expected agent's identifier missing" in new TestSetupNoJourneyRecord {
      givenSubscriptionJourneyRecordExists(providerId,
        TestData.minimalSubscriptionJourneyRecord(providerId))
      givenAuthorisedFor(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-AS-AGENT", "identifiers": [
           |    { "key":"BAR", "value": "fooArn" }
           |  ]}
           |],
           |"optionalCredentials": {"providerId": "${providerId.id}", "providerType": "GovernmentGateway"}}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withSubscribedAgent
      }
    }

    "UnsupportedAuthProvider error should redirect user to start page" in new TestSetupNoJourneyRecord {
      userLoggedInViaUnsupportedAuthProvider()
      val result = TestController.withSubscribedAgent

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/agent-subscription/finish-sign-out")
    }
  }

  "withSubscribingOrSubscribedAgent" should {
    "call body with a valid unsubscribed agent" in new TestSetupNoJourneyRecord {
      authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      val result = TestController.withSubscribingOrSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "task list"
    }
    "return the taskListSubscribed result when there is a check answers complete true flag in the session" in {

      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))

      authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
      val result = TestController.withSubscribingOrSubscribedAgent

      status(result) shouldBe 200
      bodyOf(result) shouldBe "task list"
    }
  }
}
