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
    "return valid domains" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-getExternalDomains-GET") {
        SsoStub.givenWhitelistedDomainsExist
        val result = await(connector.getWhitelistedDomains)
        result shouldBe Set("online-qa.ibt.hmrc.gov.uk", "localhost", "ibt.hmrc.gov.uk", "127.0.0.1", "www.tax.service.gov.uk")
      }
    }

    "return an empty set when the service throws an error" in {
      withMetricsTimerUpdate("ConsumedAPI-SSO-getExternalDomains-GET") {
        SsoStub.givenWhitelistedDomainsError
        val result = await(connector.getWhitelistedDomains)
        result shouldBe Set.empty
      }
    }
  }
}