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

  def verifyCheckForAcceptableNumberOfPAYEClientsUrl(times: Int) =
    verify(times, getRequestedFor(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)))

  val checkForAcceptableNumberOfSAClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-SA"

  def givenUserIsAnAgentWithAnAcceptableNumberOfSAClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(204)))

  def givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(403)))

  def givenUserIsNotAuthenticatedForSAClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(401)))

  def givenAnExceptionOccursDuringTheSAClientCheck: StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)).willReturn(aResponse().withStatus(404)))

  def verifyCheckForAcceptableNumberOfSAClients(times: Int) =
    verify(times, getRequestedFor(urlEqualTo(checkForAcceptableNumberOfSAClientsUrl)))

  val r2dwUrl = "/agent-assurance/refusal-to-deal-with"

  def givenRefusalToDealWithUtrIsForbidden(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(403)))

  def givenRefusalToDealWithUtrIsNotForbidden(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(200)))

  def givenRefusalToDealWithReturns404(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(404)))

  def verifyCheckRefusalToDealWith(times: Int, utr: String) =
    verify(times, getRequestedFor(urlEqualTo(s"$r2dwUrl/$utr")))

  val manuallyAssuredAgentUrl = (utr: String) => urlEqualTo(s"/agent-assurance/manually-assured/$utr")

  def givenAgentIsNotManuallyAssured(utr: String): StubMapping =
    stubFor(get(manuallyAssuredAgentUrl(utr))
      .willReturn(aResponse()
        .withStatus(403)))

  def givenAgentIsManuallyAssured(utr: String): StubMapping =
    stubFor(get(manuallyAssuredAgentUrl(utr))
      .willReturn(aResponse()
        .withStatus(200)))

  def givenManuallyAssuredAgentsReturns(utr: String, status: Int): StubMapping =
    stubFor(get(manuallyAssuredAgentUrl(utr))
      .willReturn(aResponse()
        .withStatus(status)))

  def verifyCheckAgentIsManuallyAssured(times: Int, utr: String) =
    verify(times, getRequestedFor(manuallyAssuredAgentUrl(utr)))

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

  def givenAGoodCombinationAndNinoNotFoundInCesa(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: String): StubMapping =
    stubFor(get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
      .willReturn(aResponse().withStatus(404)))

}
