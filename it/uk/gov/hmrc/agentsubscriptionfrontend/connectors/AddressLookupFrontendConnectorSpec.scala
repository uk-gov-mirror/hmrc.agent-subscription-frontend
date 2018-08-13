package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import uk.gov.hmrc.agentsubscriptionfrontend.models.{AddressLookupFrontendAddress, Country}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs.givenAddressLookupReturnsAddress
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class AddressLookupFrontendConnectorSpec extends BaseISpec with MetricTestSupport {

  implicit val hc = HeaderCarrier()

  "getAddressDetails" should {
    "convert the JSON returned by address-lookup-frontend into an object" in {
      withMetricsTimerUpdate("ConsumedAPI-Address-Lookup-Frontend-getAddressDetails-GET") {
        val addressId = "id"
        val addressLine1 = "10 Other Place"
        val addressLine2 = "Some District"
        val addressLine3 = "Line 3"
        val town = "Our town"
        val postcode = "AA1 1AA"
        givenAddressLookupReturnsAddress(addressId, addressLine1, addressLine2, addressLine3, town, postcode)
        val connector = app.injector.instanceOf[AddressLookupFrontendConnector]
        val address = await(connector.getAddressDetails(addressId))
        address shouldBe AddressLookupFrontendAddress(
          lines = Seq(addressLine1, addressLine2, addressLine3, town),
          postcode = Some(postcode),
          country = Country("GB", Some("United Kingdom")))
      }
    }
  }

}
