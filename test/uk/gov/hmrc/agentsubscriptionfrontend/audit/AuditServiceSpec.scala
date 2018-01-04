/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscriptionfrontend.audit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.{ Authorization, RequestId, SessionId }

class AuditServiceSpec extends UnitSpec with MockitoSugar with Eventually {

  implicit val patience = PatienceConfig(timeout = scaled(Span(500, Millis)), interval = scaled(Span(200, Millis)))

  "AuditService" should {

    "send an AgentAssurance event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      val authConnector = mock[AuthConnector]
      val service = new AuditService(mockConnector, authConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id"))
      )

      val auditData = new AuditData()

      auditData.set("utr", "abcd")
        .set("postcode", "AA1 1AA")
        .set("refuseToDealWith", false)
        .set("isEnrolledSAAgent", true)
        .set("saAgentRef", "fghij")
        .set("passSaAgentAssuranceCheck", true)
        .set("isEnrolledPAYEAgent", true)
        .set("payeAgentRef", "000/12346")
        .set("passPayeAgentAssuranceCheck", false)
        .set("authProviderId", "foo")
        .set("authProviderType", "GovernmentGateway")

      await(service.sendAgentAssuranceAuditEvent(auditData)(hc, FakeRequest("GET", "/path")))

      eventually {
        val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue shouldBe an[DataEvent]
        val sentEvent = captor.getValue

        sentEvent.auditType shouldBe "AgentAssurance"
        sentEvent.auditSource shouldBe "agent-subscription-frontend"
        sentEvent.detail("utr") shouldBe "abcd"
        sentEvent.detail("postcode") shouldBe "AA1 1AA"
        sentEvent.detail("refuseToDealWith") shouldBe "false"
        sentEvent.detail("isEnrolledSAAgent") shouldBe "true"
        sentEvent.detail("saAgentRef") shouldBe "fghij"
        sentEvent.detail("passSaAgentAssuranceCheck") shouldBe "true"
        sentEvent.detail("isEnrolledPAYEAgent") shouldBe "true"
        sentEvent.detail("payeAgentRef") shouldBe "000/12346"
        sentEvent.detail("passPayeAgentAssuranceCheck") shouldBe "false"
        sentEvent.detail("authProviderId") shouldBe "foo"
        sentEvent.detail("authProviderType") shouldBe "GovernmentGateway"

        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"

        sentEvent.tags("transactionName") shouldBe "agent-assurance"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
  }

}
