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
import play.api.http.HeaderNames.LOCATION
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.AddressLookupFrontendAddress
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class AddressLookupFrontendConnector @Inject()(http: HttpClient, metrics: Metrics, appConfig: AppConfig)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def initJourney(call: Call, journeyName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] =
    monitor(s"ConsumedAPI-Address-Lookup-Frontend-initJourney-POST-$journeyName") {
      val continueJson = Json.obj("continueUrl" -> s"${appConfig.addressLookupContinueUrl}${call.url}")

      http.POST[JsObject, HttpResponse](initJourneyUrl(journeyName), continueJson) map { resp =>
        resp.header(LOCATION).getOrElse {
          throw new ALFLocationHeaderNotSetException
        }
      }
    }

  def getAddressDetails(
    id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AddressLookupFrontendAddress] = {
    import AddressLookupFrontendAddress._

    monitor(s"ConsumedAPI-Address-Lookup-Frontend-getAddressDetails-GET") {
      http.GET[JsObject](confirmJourneyUrl(id)).map(json => (json \ "address").as[AddressLookupFrontendAddress])
    }
  }

  private def confirmJourneyUrl(id: String) =
    s"${appConfig.addressLookupFrontendBaseUrl}/api/confirmed?id=$id"

  private def initJourneyUrl(journeyName: String): String =
    s"${appConfig.addressLookupFrontendBaseUrl}/api/init/$journeyName"
}

class ALFLocationHeaderNotSetException extends NoStackTrace
