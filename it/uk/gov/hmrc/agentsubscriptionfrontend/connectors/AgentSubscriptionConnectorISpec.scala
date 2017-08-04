package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class AgentSubscriptionConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: AgentSubscriptionConnector = new AgentSubscriptionConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  private val utr = Utr("0123456789")
  "getRegistration" should {

    "return a subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = true)
      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe true
    }

    "return a not subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = false)
      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe false
    }

    "URL-path-encode path parameters" in {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(Utr("01234/56789"), "AA1 1AA/&")
      val result: Option[Registration] = await(connector.getRegistration(Utr("01234/56789"), "AA1 1AA/&"))
      result.isDefined shouldBe true
    }

    "return None when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, "AA1 1AA")
      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result shouldBe None
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      AgentSubscriptionStub.withErrorForUtrAndPostcode(utr, "AA1 1AA")
      intercept[Upstream5xxResponse] {
        await(connector.getRegistration(utr, "AA1 1AA"))
      }
    }

  }

  "subscribe" should {
    "return an ARN" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest)

      val result = await(connector.subscribeAgencyToMtd(subscriptionRequest))

      result shouldBe Arn("ARN00001")
    }

    "throw Upstream4xxResponse if subscription already exists" in {
      AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest)

      val e = intercept[Upstream4xxResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.upstreamResponseCode shouldBe 409
    }

    "throw Upstream4xxResponse if postcodes don't match" in {
      AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest)

      val e = intercept[Upstream4xxResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.upstreamResponseCode shouldBe 403
    }
  }

  private val subscriptionRequest =
    SubscriptionRequest(utr = utr,
      knownFacts = KnownFacts("AA1 2AA"),
      agency = Agency(name = "My Agency",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = Some("Anytown"),
          addressLine3 = None,
          addressLine4 = None,
          postcode = Some("AA1 1AA"),
          countryCode = "GB"),
        email = "agency@example.com",
        telephone = "0123 456 7890"))
}
