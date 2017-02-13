package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class AgentSubscriptionConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: AgentSubscriptionConnector = new AgentSubscriptionConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "getRegistration" should {

    "return RegistrationFound when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode("0123456789", "AA1 1AA")
      val result: GetRegistrationResponse = await(connector.getRegistration("0123456789", "AA1 1AA"))
      result shouldBe RegistrationFound
    }

    "URL-path-encode path parameters" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode("01234/56789", "AA1 1AA/&")
      val result: GetRegistrationResponse = await(connector.getRegistration("01234/56789", "AA1 1AA/&"))
      result shouldBe RegistrationFound
    }

    "return RegistrationNotFound when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      AgentSubscriptionStub.withNonMatchingUtrAndPostcode("0123456789", "AA1 1AA")
      val result: GetRegistrationResponse = await(connector.getRegistration("0123456789", "AA1 1AA"))
      result shouldBe RegistrationNotFound
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      AgentSubscriptionStub.withErrorForUtrAndPostcode("0123456789", "AA1 1AA")
      intercept[Upstream5xxResponse] {
        await(connector.getRegistration("0123456789", "AA1 1AA"))
      }
    }

  }

}
