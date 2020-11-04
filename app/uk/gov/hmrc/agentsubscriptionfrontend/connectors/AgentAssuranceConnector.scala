/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.http.Status._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.domain.{SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentAssuranceConnector @Inject()(http: HttpClient, metrics: Metrics, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def hasAcceptableNumberOfClients(regime: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-hasAcceptableNumberOfClients-GET") {
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}/agent-assurance/acceptableNumberOfClients/service/$regime")
        .map { response =>
          response.status match {
            case NO_CONTENT               => true
            case s if is2xx(s)            => false
            case UNAUTHORIZED | FORBIDDEN => false
            case s                        => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getActiveCesaRelationship(url: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$url")
        .map { response =>
          response.status match {
            case s if is2xx(s)         => true
            case FORBIDDEN | NOT_FOUND => false
            case s                     => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def isR2DWAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getR2DWAgents-GET") {
      val endpoint = s"/agent-assurance/refusal-to-deal-with/utr/${utr.value}"
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$endpoint")
        .map { response =>
          response.status match {
            case s if is2xx(s) => false
            case FORBIDDEN     => true
            case NOT_FOUND =>
              throw new IllegalStateException(
                s"unable to reach ${appConfig.agentAssuranceBaseUrl}$endpoint. R2dw list might not have been configured")
            case s => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def isManuallyAssuredAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET") {
      val endpoint = s"/agent-assurance/manually-assured/utr/${utr.value}"
      http
        .GET[HttpResponse](s"${appConfig.agentAssuranceBaseUrl}$endpoint")
        .map { response =>
          response.status match {
            case s if is2xx(s) => true
            case FORBIDDEN     => false
            case NOT_FOUND =>
              throw new IllegalStateException(
                s"unable to reach ${appConfig.agentAssuranceBaseUrl}/$endpoint. Manually assured agents list might not have been configured")
            case s => throw UpstreamErrorResponse(response.body, s)
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

  def hasAcceptableNumberOfVatDecOrgClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("HMCE-VATDEC-ORG")

  def hasAcceptableNumberOfIRCTClients(implicit hc: HeaderCarrier): Future[Boolean] =
    hasAcceptableNumberOfClients("IR-CT")
}
