package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import uk.gov.hmrc.agentsubscriptionfrontend.config.HttpVerbs
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http.HeaderCarrier
import com.kenshoo.play.metrics.Metrics

class SsoConnectorISpec extends BaseISpec with MetricTestSupport {

  private lazy val connector = new SsoConnector(app.injector.instanceOf[HttpVerbs],
                                                new URL(s"http://localhost:$wireMockPort"),
                                                app.injector.instanceOf[Metrics])
  private implicit val hc = HeaderCarrier()

  "SsoConnector" should {
    "return true for valid domains" in {
      givenCleanMetricRegistry()

      SsoStub.givenDomainIsWhitelisted("foo.com")
      val result = await(connector.validateExternalDomain("foo.com"))
      result shouldBe true

      timerShouldExistsAndBeenUpdated("ConsumedAPI-SSO-validateExternalDomain-GET")
    }

    "return false for a nonwhitelisted url" in {
      givenCleanMetricRegistry()

      SsoStub.givenDomainIsNotWhitelisted("invalid-example.com")
      val result = await(connector.validateExternalDomain("invalid-example.com"))
      result shouldBe false

      timerShouldExistsAndBeenUpdated("ConsumedAPI-SSO-validateExternalDomain-GET")
    }

    "return false for an invalid url" in {
      givenCleanMetricRegistry()

      SsoStub.givenDomainCheckFails("invalid-example")
      val result = await(connector.validateExternalDomain("invalid-example"))
      result shouldBe false

      timerShouldExistsAndBeenUpdated("ConsumedAPI-SSO-validateExternalDomain-GET")
    }
  }
}
