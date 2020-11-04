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

package uk.gov.hmrc.agentsubscriptionfrontend.util

import play.api.Logging
import play.api.libs.json.Reads
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpErrorFunctions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object HttpClientConverter extends Logging {

  def transformResponse[A](future: Future[HttpResponse])(implicit rds: Reads[A], ec: ExecutionContext): Future[A] =
    future.map { response =>
      response.status match {
        case s if is2xx(s) =>
          Try(response.json.as[A]) match {
            case Success(value) =>
              value
            case Failure(ex) =>
              logger.error(ex.getMessage, ex)
              throw ex
          }
        case s => throw UpstreamErrorResponse(response.body, s)
      }
    }

  def transformOptionResponse[A](future: Future[HttpResponse])(implicit rds: Reads[A], ec: ExecutionContext): Future[Option[A]] =
    future.map { response =>
      response.status match {
        case s if is2xx(s) =>
          response.body.length > 0 match {
            case true =>
              Try(response.json.as[A]) match {
                case Success(value) =>
                  Option(value)
                case Failure(ex) =>
                  logger.error(ex.getMessage, ex)
                  throw ex
              }
            case false => None
          }
        case s => throw UpstreamErrorResponse(response.body, s)
      }
    }

}
