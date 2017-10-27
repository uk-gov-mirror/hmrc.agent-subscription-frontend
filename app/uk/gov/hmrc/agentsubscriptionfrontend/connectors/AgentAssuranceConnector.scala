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

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse, Upstream4xxResponse}

import scala.concurrent.Future

@Singleton
class AgentAssuranceConnector @Inject() (@Named("agent-assurance-baseUrl") baseUrl: URL, http: HttpGet){
  def hasAcceptableNumberOfPayeClients(implicit hc: HeaderCarrier): Future[Boolean] = {
    http.GET[HttpResponse](
      new URL(baseUrl, "/agent-assurance/acceptableNumberOfClients/service/IR-PAYE").toString).map { response =>
        response.status == 204
    } recover {
      case e: Upstream4xxResponse => if ( e.upstreamResponseCode == 401 || e.upstreamResponseCode == 403 ) false else throw e
    }
  }
}
