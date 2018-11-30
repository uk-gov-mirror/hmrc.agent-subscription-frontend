package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object AgentAssuranceStub {
  def checkForAcceptableNumberOfClientsUrl(service: String) = s"/agent-assurance/acceptableNumberOfClients/service/$service"

  def givenUserIsAnAgentWithAnAcceptableNumberOfClients(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(204)))

  def givenUserIsNotAnAgentWithAnAcceptableNumberOfClients(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(403)))

  def givenUserIsNotAuthenticatedForClientCheck(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(401)))

  def givenAnExceptionOccursDuringTheClientCheck(service: String): StubMapping =
    stubFor(get(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))).willReturn(aResponse().withStatus(404)))

  def verifyCheckForAcceptableNumberOfClients(service: String, times: Int) =
    verify(times, getRequestedFor(urlEqualTo(checkForAcceptableNumberOfClientsUrl(service))))


  val r2dwUrl = "/agent-assurance/refusal-to-deal-with/utr"

  def givenRefusalToDealWithUtrIsForbidden(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(403)))

  def givenRefusalToDealWithUtrIsNotForbidden(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(200)))

  def givenRefusalToDealWithReturns404(utr: String): StubMapping =
    stubFor(get(urlEqualTo(s"$r2dwUrl/$utr")).willReturn(aResponse().withStatus(404)))

  def verifyCheckRefusalToDealWith(times: Int, utr: String) =
    verify(times, getRequestedFor(urlEqualTo(s"$r2dwUrl/$utr")))

  val manuallyAssuredAgentUrl = (utr: String) => urlEqualTo(s"/agent-assurance/manually-assured/utr/$utr")

  def givenAgentIsNotManuallyAssured(utr: String): StubMapping =
    stubFor(
      get(manuallyAssuredAgentUrl(utr))
        .willReturn(aResponse()
          .withStatus(403)))

  def givenAgentIsManuallyAssured(utr: String): StubMapping =
    stubFor(
      get(manuallyAssuredAgentUrl(utr))
        .willReturn(aResponse()
          .withStatus(200)))

  def givenManuallyAssuredAgentsReturns(utr: String, status: Int): StubMapping =
    stubFor(
      get(manuallyAssuredAgentUrl(utr))
        .willReturn(aResponse()
          .withStatus(status)))

  def verifyCheckAgentIsManuallyAssured(times: Int, utr: String) =
    verify(times, getRequestedFor(manuallyAssuredAgentUrl(utr)))

  def givenNinoAGoodCombinationAndUserHasRelationshipInCesa(
    ninoOrUtr: String,
    valueOfNinoOrUtr: String,
    saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/nino/AA123456A/saAgentReference/SA6012"))
        .willReturn(aResponse().withStatus(200)))

  def givenUtrAGoodCombinationAndUserHasRelationshipInCesa(
    ninoOrUtr: String,
    valueOfNinoOrUtr: String,
    saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/activeCesaRelationship/utr/4000000009/saAgentReference/SA6012"))
        .willReturn(aResponse().withStatus(200)))

  def givenAUserDoesNotHaveRelationshipInCesa(
    ninoOrUtr: String,
    valueOfNinoOrUtr: String,
    saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(403)))

  def givenABadCombinationAndUserHasRelationshipInCesa(
    ninoOrUtr: String,
    valueOfNinoOrUtr: String,
    saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(403)))

  def givenAGoodCombinationAndNinoNotFoundInCesa(
    ninoOrUtr: String,
    valueOfNinoOrUtr: String,
    saAgentReference: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/$saAgentReference"))
        .willReturn(aResponse().withStatus(404)))

}
