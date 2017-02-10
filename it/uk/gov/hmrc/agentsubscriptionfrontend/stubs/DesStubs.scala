package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object DesStubs {

  val addressDetails =
    """
      |{
      | "addressDetails" : {
      |   "postalCode" : "BN11 3JB"
      |  }
      |}
    """.stripMargin

  val notFoundResponse =
    """
      |{
      |   "code" : "NOT_FOUND"
      |   "reasons" : "The remote endpoint has indicated that no data can be found."
      |}
    """.stripMargin

  def utrIsValid(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/0123456789"))
      .willReturn(
        aResponse()
          .withStatus(200)
            .withBody(addressDetails)
      )
    )
  }

  def utrDoesNotExist(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/0000000000"))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(notFoundResponse)
      )
    )
  }

}
