package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

object AgentAssuranceStub {
  val checkForAcceptableNumberOfPAYEClientsUrl = "/agent-assurance/acceptableNumberOfClients/service/IR-PAYE"
  private val r2dwKey = "r2dw"

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

  val r2dwUrl = "/agent-assurance/refusal-to-deal-with/" + r2dwKey

  def givenUtrReturnedInR2DWList(utr: String): StubMapping =
    stubFor(get(urlEqualTo(r2dwUrl)).willReturn(aResponse()
      .withStatus(200)
        .withBody(s"""
                     |{
                     |   "key": "$r2dwKey",
                     |   "value": "2000000000,2000000023,$utr"
                     |}
                 """.stripMargin)
    ))

  def givenR2DWListIsEmpty: StubMapping =
    stubFor(get(urlEqualTo(r2dwUrl)).willReturn(aResponse()
      .withStatus(200)
        .withBody(s"""
                     |{
                     |   "key": "$r2dwKey",
                     |   "value": ""
                     |}
                 """.stripMargin)
    ))

  def given404ReturnedForR2dw: StubMapping =
    stubFor(get(urlEqualTo(r2dwUrl)).willReturn(aResponse().withStatus(404)))

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
