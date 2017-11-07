package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object AgentAssuranceStub {
  val checkForAcceptableNumberOfPAYEClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-PAYE"

  def givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(204)))

  def givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(403)))

  def givenUserIsNotAuthenticatedForPAYEClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(401)))

  def givenAnExceptionOccursDuringThePAYEClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(404)))

  val checkForAcceptableNumberOfSAClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-SA"

  def givenUserIsAnAgentWithAnAcceptableNumberOfSAClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(204)))

  def givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(403)))

  def givenUserIsNotAuthenticatedForSAClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(401)))

  def givenAnExceptionOccursDuringTheSAClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(404)))
}
