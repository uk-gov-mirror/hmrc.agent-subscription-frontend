package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport

trait DataStreamStubs extends Eventually {
  me: WireMockSupport =>

  override implicit val patienceConfig = PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

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

  def givenAuditConnector(): Unit = {
    stubFor(post(urlPathEqualTo(auditUrl)).willReturn(aResponse().withStatus(200)))
    stubFor(post(urlPathEqualTo(auditUrl + "/merged")).willReturn(aResponse().withStatus(200)))
  }

  private def auditUrl = "/write/audit"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
