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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.{JsObject, JsPath, Reads}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpResponse, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

case class AgencyEmail(email: Option[String])

case class AgencyEmailNotFound() extends Exception

object AgencyEmail {
  implicit val emailReads: Reads[AgencyEmail] =
    (JsPath \ "agencyEmail").readNullable[String].map(AgencyEmail(_))
}

@Singleton
class AgentServicesAccountConnector @Inject()(
  @Named("agent-services-account-baseUrl") baseUrl: URL,
  http: HttpGet,
  metrics: Metrics)(implicit ec: ExecutionContext)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getAgencyEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumerAPI-Get-AgencyEmail-GET") {
      http.GET[HttpResponse](new URL(baseUrl, "/agent-services-account/agent/agency-email").toString).map { result =>
        result.status match {
          case 200 => result.json.as[AgencyEmail].email
          case 204 => None
        }
      }
    } recoverWith {
      case _: NotFoundException => Future failed AgencyEmailNotFound()
    }

}
