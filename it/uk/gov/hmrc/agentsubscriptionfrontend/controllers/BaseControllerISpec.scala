package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{EndpointBehaviours, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseControllerISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with EndpointBehaviours {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.agent-subscription.port" -> wireMockPort
    )
    .build()

  protected implicit val materializer = app.materializer

  protected def authenticatedRequest = {
    val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
    FakeRequest().withSession(sessionKeys: _*)
  }


  protected def checkHtmlResultWithBodyText(expectedSubstring: String, result: Result): Unit = {
    status(result) shouldBe OK
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    bodyOf(result) should include(expectedSubstring)
  }
}
