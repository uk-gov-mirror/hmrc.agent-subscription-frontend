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
import javax.inject.{Inject, Named}

import play.api.libs.json.Json
import play.api.mvc.Results._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DesBusinessPartnerRecordApiConnector @Inject()(@Named("des-baseUrl") desBaseUrl: URL,
                                                     @Named("des.authorization-token") authorizationToken: String,
                                                     @Named("des.environment") environment: String,
                                                     httpGet: HttpGet) {

  def getBusinessPartnerRecord(utr: String)(implicit hc: HeaderCarrier): Future[DesBusinessPartnerRecordApiResponse] = {
    val url: String = bprUrlFor(utr)
    getWithDesHeaders(url) map { r =>
      r.status match {
        case 200 => BusinessPartnerRecordFound((Json.parse(r.body) \ "addressDetails" \ "postalCode").as[String])
        case _ => InternalError(new Status(r.status))
      }
    } recover {
      case e: NotFoundException => BusinessPartnerRecordNotFound
    }
  }

  private def bprUrlFor(utr: String): String =
    new URL(desBaseUrl, s"/registration/personal-details/${utr}").toString

  private def getWithDesHeaders(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
    httpGet.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], desHeaderCarrier)
  }

}
