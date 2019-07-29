package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.{AgentAssurance, AgentSubscriptionFrontendEvent}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

trait DataStreamStubs extends Eventually {
  me: WireMockSupport =>

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

  def verifyAuditRequestSent(
    count: Int,
    event: AgentSubscriptionFrontendEvent,
    tags: Map[String, String] = Map.empty,
    detail: Map[String, String] = Map.empty): Unit =
    eventually {
      verify(
        count,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
                                            |  "auditSource": "agent-subscription-frontend",
                                            |  "auditType": "$event",
                                            |  "tags": ${Json.toJson(tags)},
                                            |  "detail": ${Json.toJson(detail)}
                                            |}""".stripMargin))
      )
    }

  def verifyAuditRequestNotSent(event: AgentSubscriptionFrontendEvent): Unit =
    eventually {
      verify(
        0,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
                                            |  "auditSource": "agent-subscription-frontend",
                                            |  "auditType": "$event"
                                            |}""".stripMargin))
      )
    }

  def givenAuditConnector(): StubMapping = {
    stubFor(post(urlPathEqualTo(auditUrl + "/merged")).willReturn(aResponse().withStatus(204)))
    stubFor(post(urlPathEqualTo(auditUrl)).willReturn(aResponse().withStatus(204)))
  }

  private def auditUrl = "/write/audit"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

  def verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
                                                                identifier: TaxIdentifier,
                                                                passCESAAgentAssuranceCheck: Boolean,
                                                                saAgentRef: String,
                                                                aAssurancePayeCheck: Boolean): Unit = {

    val clientIdentifier = identifier match {
      case nino@Nino(_) => "userEnteredNino" -> nino.value
      case utr@Utr(_) => "userEnteredUtr" -> utr.value
    }
    val payeAudit = if (aAssurancePayeCheck) Seq("passPayeAgentAssuranceCheck" -> "false") else Seq.empty

    verifyAuditRequestSent(
      1,
      AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "false",
        "passSaAgentAssuranceCheck" -> "false",
        "isEnrolledPAYEAgent" -> "false",
        "passCESAAgentAssuranceCheck" -> passCESAAgentAssuranceCheck.toString,
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway",
        "userEnteredSaAgentRef" -> saAgentRef
      ) + clientIdentifier ++ payeAudit,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }

  def verifyAgentAssuranceAuditRequestSent(
                                            passPayeAgentAssuranceCheck: Option[Boolean],
                                            passSaAgentAssuranceCheck: Option[Boolean],
                                            passVatDecOrgAgentAssuranceCheck: Option[Boolean],
                                            passIRCTAgentAssuranceCheck: Option[Boolean]): Unit = {
    val optional = Seq(
      passPayeAgentAssuranceCheck.map("passPayeAgentAssuranceCheck" -> _.toString),
      passSaAgentAssuranceCheck.map("passSaAgentAssuranceCheck" -> _.toString),
      passVatDecOrgAgentAssuranceCheck.map("passVatDecOrgAgentAssuranceCheck" -> _.toString),
      passIRCTAgentAssuranceCheck.map("passIRCTAgentAssuranceCheck" -> _.toString)).flatten

    verifyAuditRequestSent(
      1,
      AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "true",
        "saAgentRef" -> "FOO1234",
        //TODO "refuseToDealWith" -> ?,
        "isEnrolledPAYEAgent" -> "true",
        "payeAgentRef" -> "HZ1234",
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway"
      ) ++ optional,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }

}
