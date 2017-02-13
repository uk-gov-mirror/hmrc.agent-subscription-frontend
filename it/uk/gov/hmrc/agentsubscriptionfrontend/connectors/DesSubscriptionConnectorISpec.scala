package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.DesStubs
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class DesSubscriptionConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector: DesBusinessPartnerRecordApiConnector = new HttpDesBusinessPartnerRecordApiConnector(new URL(s"http://localhost:${wireMockPort}"), "auth-token", "des-env", WSHttp)

  "DES Subscription Connector" should {

    "return a postal code for a found UTR" in {
      DesStubs.utrIsValid()
      val result: DesBusinessPartnerRecordApiResponse = await(connector.getBusinessPartnerRecord("0123456789"))
      result shouldBe BusinessPartnerRecordFound("BN11 3JB")
    }

    "return not found status for a not found UTR" in {
      DesStubs.utrDoesNotExist()
      val result: DesBusinessPartnerRecordApiResponse = await(connector.getBusinessPartnerRecord("0000000000"))
      result shouldBe BusinessPartnerRecordNotFound()
    }

  }

}
