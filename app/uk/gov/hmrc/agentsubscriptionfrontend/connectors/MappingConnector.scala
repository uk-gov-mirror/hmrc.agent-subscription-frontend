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
import play.api.libs.json.JsValue
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

@Singleton
class MappingConnector @Inject()(
  @Named("agent-mapping-baseUrl") baseUrl: URL,
  http: HttpGet with HttpPost with HttpPut with HttpDelete,
  metrics: Metrics)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val mappingUrl = new URL(baseUrl, s"/agent-mapping/mappings")

  def createPreSubscription(utr: Utr)(implicit hc: HeaderCarrier): Future[MappingEligibility] =
    monitor(s"ConsumedAPI-Agent-Mapping-createPreSubscription-PUT") {
      http
        .PUT[String, HttpResponse](s"$mappingUrl/pre-subscription/utr/${utr.value}", "")
        .map(_ => MappingEligibility.IsEligible)
        .recover {
          case ex: Upstream4xxResponse if ex.upstreamResponseCode == 409 => MappingEligibility.IsEligible
          case ex: Upstream4xxResponse if ex.upstreamResponseCode == 403 => MappingEligibility.IsNotEligible
        }
    }

  def updatePreSubscriptionWithArn(utr: Utr)(implicit hc: HeaderCarrier): Future[Unit] =
    monitor(s"ConsumedAPI-Agent-Mapping-updatePreSubscriptionWithArn-PUT") {
      http
        .PUT[String, HttpResponse](s"$mappingUrl/post-subscription/utr/${utr.value}", "")
        .map(_ => ())
    }

  def deletePreSubscription(utr: Utr)(implicit hc: HeaderCarrier): Future[Unit] =
    monitor(s"ConsumedAPI-Agent-Mapping-deletePreSubscription-DELETE") {
      http
        .DELETE(s"$mappingUrl/pre-subscription/utr/${utr.value}")
        .map(_ => ())
    }
}
