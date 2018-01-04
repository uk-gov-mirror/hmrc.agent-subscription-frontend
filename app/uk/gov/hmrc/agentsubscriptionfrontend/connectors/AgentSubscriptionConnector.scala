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
import javax.inject.{Inject, Named, Singleton}

import play.api.libs.json.JsValue
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Arn, Registration, SubscriptionRequest}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpPost}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

@Singleton
class AgentSubscriptionConnector @Inject()(@Named("agent-subscription-baseUrl") baseUrl: URL, http: HttpGet with HttpPost) {

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier): Future[Option[Registration]] = {
    val url = getRegistrationUrlFor(utr, postcode)
    http.GET[Option[Registration]](url)
  }

  def subscribeAgencyToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[Arn] =
    http.POST[SubscriptionRequest, JsValue](subscriptionUrl.toString, subscriptionRequest) map { js =>
      (js \ "arn").as[Arn]
    }

  private val subscriptionUrl = new URL(baseUrl, s"/agent-subscription/subscription")

  private def getRegistrationUrlFor(utr: Utr, postcode: String) =
    new URL(baseUrl, s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}").toString
}
