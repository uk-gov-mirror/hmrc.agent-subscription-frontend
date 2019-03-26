package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http._
import com.kenshoo.play.metrics.Metrics
import scala.concurrent.ExecutionContext.Implicits.global
class AgentSubscriptionConnectorISpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: AgentSubscriptionConnector =
    new AgentSubscriptionConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpGet with HttpPost with HttpPut],
      app.injector.instanceOf[Metrics])

  private val utr = Utr("0123456789")
  private val crn = CompanyRegistrationNumber("SC123456")

  "getRegistration" should {

    "return a subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub
          .withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = true, isSubscribedToETMP = true)
        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result.isDefined shouldBe true
        result.get.taxpayerName shouldBe Some("My Agency")
        result.get.isSubscribedToAgentServices shouldBe true
        result.get.isSubscribedToETMP shouldBe true
        testBusinessAddress(result.get)
        result.get.emailAddress shouldBe Some("someone@example.com")
      }
    }

    "return a not subscribed Registration when agent-subscription returns a 200 response (for a matching UTR and postcode)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToAgentServices = false)

        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result.isDefined shouldBe true
        result.get.taxpayerName shouldBe Some("My Agency")
        result.get.isSubscribedToAgentServices shouldBe false
        testBusinessAddress(result.get)
        result.get.emailAddress shouldBe Some("someone@example.com")
      }
    }

    "return a not subscribed with record in ETMP Registration when partially subscribed" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub
          .withMatchingUtrAndPostcode(utr, "AA1 1AA", isSubscribedToETMP = true, isSubscribedToAgentServices = false)

        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result.isDefined shouldBe true
        result.get.taxpayerName shouldBe Some("My Agency")
        result.get.isSubscribedToETMP shouldBe true
        result.get.isSubscribedToAgentServices shouldBe false
        testBusinessAddress(result.get)
        result.get.emailAddress shouldBe Some("someone@example.com")
      }
    }

    "URL-path-encode path parameters" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withMatchingUtrAndPostcode(Utr("01234/56789"), "AA1 1AA/&")

        val result: Option[Registration] = await(connector.getRegistration(Utr("01234/56789"), "AA1 1AA/&"))
        result.isDefined shouldBe true
      }
    }

    "return None when agent-subscription returns a 404 response (for a non-matching UTR and postcode)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, "AA1 1AA")

        val result: Option[Registration] = await(connector.getRegistration(utr, "AA1 1AA"))
        result shouldBe None
      }
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
        AgentSubscriptionStub.withErrorForUtrAndPostcode(utr, "AA1 1AA")

        intercept[Upstream5xxResponse] {
          await(connector.getRegistration(utr, "AA1 1AA"))
        }
      }
    }

    def testBusinessAddress(registration: Registration): Unit = {
      registration.address.addressLine1 shouldBe "AddressLine1 A"
      registration.address.addressLine2 shouldBe Some("AddressLine2 A")
      registration.address.addressLine3 shouldBe Some("AddressLine3 A")
      registration.address.addressLine4 shouldBe Some("AddressLine4 A")
    }
  }

  "subscribe" should {
    "return an ARN" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest)

        val result = await(connector.subscribeAgencyToMtd(subscriptionRequest))

        result shouldBe Arn("ARN00001")
      }
    }

    "throw Upstream4xxResponse if subscription already exists" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest)

        val e = intercept[Upstream4xxResponse] {
          await(connector.subscribeAgencyToMtd(subscriptionRequest))
        }

        e.upstreamResponseCode shouldBe 409
      }
    }

    "throw Upstream4xxResponse if postcodes don't match" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
        AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest)

        val e = intercept[Upstream4xxResponse] {
          await(connector.subscribeAgencyToMtd(subscriptionRequest))
        }

        e.upstreamResponseCode shouldBe 403
      }
    }
  }

  "completePartialSubscription" should {
    "return an ARN when partialSubscription has been resolved" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillSucceed(partialSubscriptionRequest)

        val result = await(connector.completePartialSubscription(partialSubscriptionRequest))

        result shouldBe Arn("ARN00001")
      }
    }

    "throw Upstream4xxResponse if enrolment is already allocated to someone" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 409)

        val e = intercept[Upstream4xxResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.upstreamResponseCode shouldBe 409
      }
    }

    "throw Upstream4xxResponse if details do not match any record" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 403)

        val e = intercept[Upstream4xxResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.upstreamResponseCode shouldBe 403
      }
    }

    "throw BadRequestException if BadRequest" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 400)

        val e = intercept[BadRequestException] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.responseCode shouldBe 400
      }
    }

    "throw Upstream5xxResponse if postcodes don't match" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
        AgentSubscriptionStub.partialSubscriptionWillReturnStatus(partialSubscriptionRequest, 500)

        val e = intercept[Upstream5xxResponse] {
          await(connector.completePartialSubscription(partialSubscriptionRequest))
        }

        e.upstreamResponseCode shouldBe 500
      }
    }
  }

  "matchCorporationTaxUtrWithCrn" should {

    "return true when agent-subscription returns a 200 response (for a matching UTR and CRN)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub
          .withMatchingCtUtrAndCrn(utr, crn)

        await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe true
      }
    }

    "return false when agent-subscription returns a 404 response (for a non-matching UTR and CRN)" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub.withNonMatchingCtUtrAndCrn(utr, crn)

        await(connector.matchCorporationTaxUtrWithCrn(utr, crn)) shouldBe false
      }
    }

    "throw an exception when agent-subscription returns a 500 response" in {
      withMetricsTimerUpdate("ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
        AgentSubscriptionStub.withErrorForCtUtrAndCrn(utr, crn)

        intercept[Upstream5xxResponse] {
          await(connector.matchCorporationTaxUtrWithCrn(utr, crn))
        }
      }
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
        email = "agency@example.com"
      )
    )

  private val partialSubscriptionRequest =
    CompletePartialSubscriptionBody(
      utr = utr,
      knownFacts = SubscriptionRequestKnownFacts("AA1 2AA"))
}