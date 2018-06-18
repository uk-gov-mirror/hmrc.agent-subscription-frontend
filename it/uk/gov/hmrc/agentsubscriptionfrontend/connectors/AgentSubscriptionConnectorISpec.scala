package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http._
import com.kenshoo.play.metrics.Metrics

class AgentSubscriptionConnectorISpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: AgentSubscriptionConnector =
    new AgentSubscriptionConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpGet with HttpPost],
      app.injector.instanceOf[Metrics])

  private val utr = Utr("0123456789")
  "getRegistration" should {

    "return a subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      givenCleanMetricRegistry()

      AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = true)
      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe true

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET")
    }

    "return a not subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = false)

      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result.isDefined shouldBe true
      result.get.taxpayerName shouldBe Some("My Agency")
      result.get.isSubscribedToAgentServices shouldBe false

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET")
    }

    "URL-path-encode path parameters" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.withMatchingUtrAndPostcode(Utr("01234/56789"), "AA1 1AA/&")

      val result: Option[Registration] = await(connector.getRegistration(Utr("01234/56789"), "AA1 1AA/&"))
      result.isDefined shouldBe true

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET")
    }

    "return None when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, "AA1 1AA")

      val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
      result shouldBe None

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET")
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.withErrorForUtrAndPostcode(utr, "AA1 1AA")

      intercept[Upstream5xxResponse] {
        await(connector.getRegistration(utr, "AA1 1AA"))
      }

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET")
    }
  }

  "subscribe" should {
    "return an ARN" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest)

      val result = await(connector.subscribeAgencyToMtd(subscriptionRequest))

      result shouldBe Arn("ARN00001")

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST")
    }

    "throw Upstream4xxResponse if subscription already exists" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest)

      val e = intercept[Upstream4xxResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.upstreamResponseCode shouldBe 409

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST")
    }

    "throw Upstream4xxResponse if postcodes don't match" in {
      givenCleanMetricRegistry()
      AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest)

      val e = intercept[Upstream4xxResponse] {
        await(connector.subscribeAgencyToMtd(subscriptionRequest))
      }

      e.upstreamResponseCode shouldBe 403

      timerShouldExistsAndBeenUpdated("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST")
    }
  }

  private val subscriptionRequest =
    SubscriptionRequest(
      utr = utr,
      knownFacts = SubscriptionRequestKnownFacts("AA1 2AA"),
      agency = Agency(
        name = "My Agency",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = Some("Anytown"),
          addressLine3 = None,
          addressLine4 = None,
          postcode = "AA1 1AA",
          countryCode = "GB"),
        email = "agency@example.com",
        telephone = "0123 456 7890"
      )
    )
}