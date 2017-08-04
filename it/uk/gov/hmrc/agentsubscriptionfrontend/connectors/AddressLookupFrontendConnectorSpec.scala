package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import uk.gov.hmrc.agentsubscriptionfrontend.models.{AddressLookupAddress, Country}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs.givenAddressLookupReturnsAddress
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class AddressLookupFrontendConnectorSpec extends BaseISpec {

  implicit val hc = HeaderCarrier()

  "getAddressDetails" should {
    "convert the JSON returned by address-lookup-frontend into an object" in {
      val addressId = "id"
      val addressLine1 = "10 Other Place"
      val addressLine2 = "Some District"
      val addressLine3 = "Line 3"
      val town = "Our town"
      val postcode = "AA1 1AA"
      givenAddressLookupReturnsAddress(addressId, addressLine1, addressLine2, addressLine3, town, postcode)
      val connector = app.injector.instanceOf[AddressLookupFrontendConnector]
      val address = await(connector.getAddressDetails(addressId))
      address shouldBe AddressLookupAddress(
        lines = Seq(
          addressLine1,
          addressLine2,
          addressLine3,
          town),
        postcode = Some(postcode),
        country = Country("GB",Some("United Kingdom"))
      )

    }
  }

}
