package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class AuthenticatorConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {
  private implicit val hc = HeaderCarrier()

  private lazy val connector: AuthenticatorConnector = new AuthenticatorConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "refreshEnrolments" should {
    "return true for successful enrolments refresh" in {
      AuthStub.refreshEnrolmentsSuccess
      await(connector.refreshEnrolments)

      WireMock.verify(1, WireMock.postRequestedFor(urlEqualTo("/government-gateway-authentication/refresh-profile")))
    }

    "return false for expired GG token" in {
      AuthStub.refreshEnrolmentsGGTokenHasExpired

      intercept[Upstream4xxResponse] {
        await(connector.refreshEnrolments)
      }
    }

    "return false for non GG token" in {
      AuthStub.refreshEnrolmentsNonGGToken

      intercept[Upstream4xxResponse] {
        await(connector.refreshEnrolments)
      }
    }
  }
}
