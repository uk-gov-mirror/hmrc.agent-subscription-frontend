package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object AgentAssuranceStub {
  val checkForAcceptableNumberOfPAYEClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-PAYE"

  def givenUserIsAnAgentWithAnAccetableNumberOfPAYEClients = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(204)))
  }

  def givenUserIsNotAnAgentWithAnAccetableNumberOfPAYEClients = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(403)))
  }

  def givenUserIsNotAuthenticatedForPAYEClientCheck = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(401)))
  }

  def givenAnExceptionOccursDuringhThePAYEClientCheck = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfPAYEClientsUrl)).willReturn(aResponse().withStatus(404)))
  }

  val checkForAcceptableNumberOfSAClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-SA"

  def givenUserIsAnAgentWithAnAccetableNumberOfSAClients = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(204)))
  }

  def givenUserIsNotAnAgentWithAnAccetableNumberOfSAClients = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(403)))
  }

  def givenUserIsNotAuthenticatedForSAClientCheck = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(401)))
  }

  def givenAnExceptionOccursDuringhTheSAClientCheck = {
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(404)))
  }
}
