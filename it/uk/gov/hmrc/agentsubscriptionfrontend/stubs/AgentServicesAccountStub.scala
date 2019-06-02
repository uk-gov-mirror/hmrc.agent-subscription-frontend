package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object AgentServicesAccountStub {

  val agentServicesAccountUrl = s"/agent-services-account/agent/agency-email"

  def givenGetEmailStub: StubMapping = {
    stubFor(get(urlEqualTo(agentServicesAccountUrl))
      .willReturn(aResponse()
        .withStatus(200).withBody(
          """{
            |   "agencyEmail":"test@gmail.com"
            |}""".stripMargin)))
  }

  def givenNoEmailStub: StubMapping = {
    stubFor(get(urlEqualTo(agentServicesAccountUrl))
      .willReturn(aResponse()
        .withStatus(204)))
  }

  def givenNotFoundEmailStub: StubMapping = {
    stubFor(get(urlEqualTo(agentServicesAccountUrl))
      .willReturn(aResponse()
        .withStatus(404)))
  }

}
