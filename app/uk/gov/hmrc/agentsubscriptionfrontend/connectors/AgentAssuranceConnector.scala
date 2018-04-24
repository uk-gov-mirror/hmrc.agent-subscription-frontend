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

package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.{SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

@Singleton
class AgentAssuranceConnector @Inject()(@Named("agent-assurance-baseUrl") baseUrl: URL, http: HttpGet, metrics: Metrics)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def hasAcceptableNumberOfClients(regime: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-hasAcceptableNumberOfClients-GET") {
      http
        .GET[HttpResponse](new URL(baseUrl, s"/agent-assurance/acceptableNumberOfClients/service/$regime").toString)
        .map { response =>
          response.status == 204
        } recover {
        case e: Upstream4xxResponse =>
          if (e.upstreamResponseCode == 401 || e.upstreamResponseCode == 403) false else throw e
      }
    }

  def getActiveCesaRelationship(url: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
      http
        .GET[HttpResponse](baseUrl + url)
        .map(_ => true)
        .recover {
          case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => false
          case _: NotFoundException                                    => false
        }
    }

  def isR2DWAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getR2DWAgents-GET") {
      val endpoint = s"/agent-assurance/refusal-to-deal-with/utr/${utr.value}"
      http
        .GET[HttpResponse](new URL(baseUrl, endpoint).toString)
        .map { response =>
          response.status == 403
        }
        .recover {
          case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => true
          case _: NotFoundException => {
            throw new IllegalStateException(
              s"unable to reach $baseUrl/$endpoint. R2dw list might not have been configured")
          }
        }
    }

  def isManuallyAssuredAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET") {
      val endpoint = s"/agent-assurance/manually-assured/utr/${utr.value}"
      http
        .GET[HttpResponse](new URL(baseUrl, endpoint).toString)
        .map { response =>
          (200 until 300) contains response.status
        }
        .recover {
          case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => false
          case _: NotFoundException => {
            throw new IllegalStateException(
              s"unable to reach $baseUrl/$endpoint. Manually assured agents list might not have been configured")
          }
        }
    }

  private def cesaGetUrl(ninoOrUtr: String, valueOfNinoOrUtr: String, saAgentReference: SaAgentReference): String =
    s"/agent-assurance/activeCesaRelationship/$ninoOrUtr/$valueOfNinoOrUtr/saAgentReference/${saAgentReference.value}"

  def hasActiveCesaRelationship(ninoOrUtr: TaxIdentifier, taxIdName: String, saAgentReference: SaAgentReference)(
    implicit hc: HeaderCarrier): Future[Boolean] =
    getActiveCesaRelationship(cesaGetUrl(taxIdName, ninoOrUtr.value, saAgentReference))

  def hasAcceptableNumberOfPayeClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-PAYE")

  def hasAcceptableNumberOfSAClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-SA")
}
