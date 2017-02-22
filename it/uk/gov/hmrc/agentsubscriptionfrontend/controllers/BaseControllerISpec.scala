package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
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

}
