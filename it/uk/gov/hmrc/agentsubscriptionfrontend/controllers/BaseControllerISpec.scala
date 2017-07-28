package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.google.inject.AbstractModule
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.agentsubscriptionfrontend.support.{EndpointBehaviours, MongoApp, SampleUser, TestSessionStoreService, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseControllerISpec extends UnitSpec with OneAppPerSuite with MongoApp with WireMockSupport with EndpointBehaviours {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.agent-subscription.port" -> wireMockPort,
        "microservice.services.address-lookup-frontend.port" -> wireMockPort,
        "passcodeAuthentication.enabled" -> passcodeAuthenticationEnabled
      )
      .configure(mongoConfiguration)
      .overrides(new TestGuiceModule)
  }

  protected def passcodeAuthenticationEnabled: Boolean = false

  protected lazy val sessionStoreService = new TestSessionStoreService

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[SessionStoreService]).toInstance(sessionStoreService)
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    sessionStoreService.clear()
  }

  protected implicit val materializer = app.materializer

  protected def authenticatedRequest(user: SampleUser = subscribingAgent): FakeRequest[AnyContentAsEmpty.type] = {
    val sessionKeys = AuthStub.userIsAuthenticated(user)
    FakeRequest().withSession(sessionKeys: _*)
  }


  protected def checkHtmlResultWithBodyText(result: Result, expectedSubstrings: String*): Unit = {
    status(result) shouldBe OK
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    expectedSubstrings.foreach(s => bodyOf(result) should include(s))
  }

  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String): String = HtmlFormat.escape(Messages(key)).toString

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers, Some(request.session))

}


