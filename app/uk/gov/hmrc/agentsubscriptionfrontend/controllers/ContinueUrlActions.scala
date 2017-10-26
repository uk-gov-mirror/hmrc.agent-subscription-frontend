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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{HostnameWhiteListService, SessionStoreService}
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

@Singleton
class ContinueUrlActions @Inject()(whiteListService: HostnameWhiteListService,
                                   sessionStoreService: SessionStoreService) {

  private def extractContinueUrl[A](implicit request: Request[A]): Future[Option[ContinueUrl]] = {
    implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, Option(request.session))

    request.getQueryString("continue") match {
      case Some(continueUrl) =>
        Try(ContinueUrl(continueUrl)) match {
          case Success(url) =>
            isRelativeOrAbsoluteWhiteListed(url).collect {
              case true => Some(url)
            }.recover {
              case NonFatal(e) =>
                Logger.warn(s"Check for whitelisted hostname failed", e)
                None
            }
          case Failure(e) =>
            Logger.warn(s"$continueUrl is not a valid continue URL", e)
            Future.successful(None)
        }
      case None =>
        Future.successful(None)
    }
  }

  def withMaybeContinueUrl[A](block: Option[ContinueUrl] => Future[Result])(implicit request: Request[A], hc: HeaderCarrier): Future[Result] = {
    val continueUrl: Future[Option[ContinueUrl]] = extractContinueUrl
    continueUrl.flatMap(block(_))
  }

  def withMaybeContinueUrlCached[A](block: => Future[Result])(implicit hc: HeaderCarrier, request: Request[A]): Future[Result] = {
      withMaybeContinueUrl {
        case None => block
        case Some(url) =>
          sessionStoreService.cacheContinueUrl(url).flatMap(_ => block)
      }
  }

  private def isRelativeOrAbsoluteWhiteListed(continueUrl: ContinueUrl)(implicit hc: HeaderCarrier): Future[Boolean] = {
    if (!continueUrl.isRelativeUrl) whiteListService.isAbsoluteUrlWhiteListed(continueUrl)
    else Future.successful(true)
  }
}