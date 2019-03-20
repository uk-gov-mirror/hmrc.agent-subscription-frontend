package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}
import com.kenshoo.play.metrics.Metrics
import scala.concurrent.ExecutionContext.Implicits.global

class SsoConnectorISpec extends BaseISpec with MetricTestSupport {

  private lazy val connector = new SsoConnector(
    app.injector.instanceOf[HttpGet],
    new URL(s"http://localhost:$wireMockPort"),
    app.injector.instanceOf[Metrics])
  private implicit val hc = HeaderCarrier()

  "SsoConnector" should {
    "return true for valid domains" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-validateExternalDomain-GET") {
        SsoStub.givenDomainIsWhitelisted("foo.com")
        val result = await(connector.validateExternalDomain("foo.com"))
        result shouldBe true
      }
    }

    "return false for a non whitelisted url" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-validateExternalDomain-GET") {
        SsoStub.givenDomainIsNotWhitelisted("invalid-example.com")
        val result = await(connector.validateExternalDomain("invalid-example.com"))
        result shouldBe false
      }
    }

    "return false for an invalid url" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-validateExternalDomain-GET") {
        SsoStub.givenDomainCheckFails("invalid-example")
        val result = await(connector.validateExternalDomain("invalid-example"))
        result shouldBe false
      }
    }
  }
}