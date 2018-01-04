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

  def givenNinoAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/nino/AA123456A/saAgentReference/SA6012"))
      .willReturn(aResponse().withStatus(200)))

  def givenUtrAGoodCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/utr/4000000009/saAgentReference/SA6012"))
      .willReturn(aResponse().withStatus(200)))

  def givenAUserDoesNotHaveRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
      .willReturn(aResponse().withStatus(403)))

  def givenABadCombinationAndUserHasRelationshipInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
      .willReturn(aResponse().withStatus(403)))
}
