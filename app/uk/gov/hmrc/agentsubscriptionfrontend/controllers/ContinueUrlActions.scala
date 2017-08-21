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
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

@Singleton
class ContinueUrlActions @Inject()(whiteListService: HostnameWhiteListService,
                                   sessionStoreService: SessionStoreService) {

  def withMaybeContinueUrlCached[A](block: => Result)(implicit request: Request[A]): Future[Result] = {
    val session = if (request.session.isEmpty)
      request.session + (SessionKeys.sessionId -> s"session-${UUID.randomUUID().toString}")
    else request.session

    implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, Some(session))

    val result: Future[_] = request.getQueryString("continue") match {
      case Some(continueUrl) =>
        Try(ContinueUrl(continueUrl)) match {
          case Success(url) =>
            isRelativeOrAbsoluteWhiteListed(url).collect {
              case true => sessionStoreService.cacheContinueUrl(url)
            }.recover {
              case NonFatal(e) =>
                Logger.warn(s"Check for whitelisted hostname failed", e)
            }
          case Failure(e) =>
            Logger.warn(s"$continueUrl is not a valid continue URL", e)
            Future.successful(())
        }
      case None =>
        Future.successful(())
    }

    result.map(_ => block.withSession(session))
  }

  private def isRelativeOrAbsoluteWhiteListed(continueUrl: ContinueUrl)(implicit hc: HeaderCarrier): Future[Boolean] = {
    if (!continueUrl.isRelativeUrl) whiteListService.isAbsoluteUrlWhiteListed(continueUrl)
    else Future.successful(true)
  }
}
