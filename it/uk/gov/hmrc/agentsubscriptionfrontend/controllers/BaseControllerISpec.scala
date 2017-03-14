package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.google.inject.AbstractModule
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{EndpointBehaviours, TestSessionStoreService, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseControllerISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with EndpointBehaviours {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.agent-subscription.port" -> wireMockPort
      )
      .overrides(new TestGuiceModule)
  }

  protected lazy val sessionStoreService = new TestSessionStoreService

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[SessionStoreService]).toInstance(sessionStoreService)
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    sessionStoreService.reset()
  }

  protected implicit val materializer = app.materializer

  protected def authenticatedRequest() = {
    val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
    FakeRequest().withSession(sessionKeys: _*)
  }


  protected def checkHtmlResultWithBodyText(result: Result, expectedSubstrings: String*): Unit = {
    status(result) shouldBe OK
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    expectedSubstrings.foreach(s => bodyOf(result) should include(s))
  }
}
