package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.play.http.HeaderCarrier

class SsoConnectorSpec extends BaseISpec {

  private lazy val connector = new SsoConnector(WSHttp, new URL(s"http://localhost:$wireMockPort"))
  private implicit val hc = HeaderCarrier()

  "SsoConnector" should {
    "return true for valid domains" in {
      SsoStub.givenDomainIsWhitelisted("foo.com")
      val result = await(connector.validateExternalDomain("foo.com"))
      result shouldBe true
    }

    "return false for a nonwhitelisted url" in {
      SsoStub.givenDomainIsNotWhitelisted("invalid-example.com")
      val result = await(connector.validateExternalDomain("invalid-example.com"))
      result shouldBe false
    }

    "return false for an invalid url" in {
      SsoStub.givenDomainCheckFails("invalid-example")
      val result = await(connector.validateExternalDomain("invalid-example"))
      result shouldBe false
    }
  }
}
