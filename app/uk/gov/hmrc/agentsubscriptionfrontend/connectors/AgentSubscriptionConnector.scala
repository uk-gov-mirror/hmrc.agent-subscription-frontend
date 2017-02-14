/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.gov.hmrc.agentsubscriptionfrontend.models.Registration
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse, NotFoundException}

import scala.concurrent.Future

@Singleton
class AgentSubscriptionConnector @Inject() (@Named("agent-subscription-baseUrl") baseUrl: URL, httpGet: HttpGet) {

  def getRegistration(utr: String, postcode: String)(implicit hc: HeaderCarrier): Future[Option[Registration]] = {
    val url = getRegistrationUrlFor(utr, postcode)
    httpGet.GET[HttpResponse](url).map { response: HttpResponse =>
      response.status match {
        case 200 => Some(new Registration)
      }
    } recover {
      case e: NotFoundException => None
    }
  }

  private def getRegistrationUrlFor(utr: String, postcode: String) =
    new URL(baseUrl, s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}").toString
}
