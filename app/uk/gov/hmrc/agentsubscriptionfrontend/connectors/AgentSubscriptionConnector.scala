/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{CompanyRegistrationNumber, CompletePartialSubscriptionBody, Registration, SubscriptionRequest}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentSubscriptionConnector @Inject()(
  @Named("agent-subscription-baseUrl") baseUrl: URL,
  http: HttpGet with HttpPost with HttpPut,
  metrics: Metrics)(implicit ec: ExecutionContext)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier): Future[Option[Registration]] =
    monitor(s"ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
      val url = getRegistrationUrlFor(utr, postcode)
      http.GET[Option[Registration]](url)
    }

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: CompanyRegistrationNumber)(
    implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
      val url = new URL(
        baseUrl,
        s"/agent-subscription/corporation-tax-utr/${encodePathSegment(utr.value)}/crn/${encodePathSegment(crn.value)}")

      http
        .GET[HttpResponse](url.toString)
        .map(_.status == 200)
        .recover {
          case _: NotFoundException => false
        }
    }

  def subscribeAgencyToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
      http.POST[SubscriptionRequest, JsValue](subscriptionUrl.toString, subscriptionRequest) map { js =>
        (js \ "arn").as[Arn]
      }
    }

  def completePartialSubscription(details: CompletePartialSubscriptionBody)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
      http.PUT[CompletePartialSubscriptionBody, JsValue](subscriptionUrl.toString, details).map { js =>
        (js \ "arn").as[Arn]
      }
    }

  private val subscriptionUrl = new URL(baseUrl, s"/agent-subscription/subscription")

  private def getRegistrationUrlFor(utr: Utr, postcode: String) =
    new URL(
      baseUrl,
      s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}").toString
}
