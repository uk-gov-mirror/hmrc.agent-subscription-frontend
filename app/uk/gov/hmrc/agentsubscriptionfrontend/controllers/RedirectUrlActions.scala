/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.SsoConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.MongoDBSessionStoreService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromWhitelist, RedirectUrl, UnsafePermitAll}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * RedirectUrl class provides methods to validate a continue url provided as a query parameter.
  * To retrieve the url from the RedirectUrl class you need to provide a RedirectUrlPolicy which defines a set of rules
  * to validate the url.
  */

@Singleton
class RedirectUrlActions @Inject()(sessionStoreService: MongoDBSessionStoreService, ssoConnector: SsoConnector)(implicit executor: ExecutionContext)
    extends Logging {

  def whitelistedDomains()(implicit hc: HeaderCarrier): Future[Set[String]] = ssoConnector.getWhitelistedDomains()

  def extractRedirectUrl[A](implicit request: Request[A]): Option[RedirectUrl] =
    request.getQueryString("continue") match {
      case Some(redirectUrl) =>
        Try(RedirectUrl(redirectUrl)) match {
          case Success(url) => Some(url)
          case Failure(e) =>
            logger.warn(s"[$redirectUrl] is not a valid redirect URL, $e")
            None
        }
      case None =>
        None
    }

  def checkRedirectUrlAndContinue[A](redirectUrl: RedirectUrl, block: Option[String] => Future[A])(implicit hc: HeaderCarrier): Future[A] = {
    val whitelistPolicy = AbsoluteWithHostnameFromWhitelist(whitelistedDomains)
    val unsafeUrl = redirectUrl.get(UnsafePermitAll).url

    //if relative let through else check url domain is on whitelist
    if (RedirectUrl.isRelativeUrl(unsafeUrl)) block(Some(unsafeUrl))
    else
      redirectUrl.getEither(whitelistPolicy).flatMap {
        case Right(safeRedirectUrl) => block(Some(safeRedirectUrl.url))
        case Left(errorMessage) =>
          logger.warn(s"url does not comply with whitelist policy, removing redirect url... $errorMessage")
          block(None)
      }
  }

  def withMaybeRedirectUrl[A](block: Option[String] => Future[Result])(implicit request: Request[A], hc: HeaderCarrier): Future[Result] =
    extractRedirectUrl match {
      case Some(redirectUrl) => checkRedirectUrlAndContinue[Result](redirectUrl, block)
      case None              => block(None)
    }

  def withMaybeRedirectUrlCached[A](block: => Future[Result])(implicit hc: HeaderCarrier, request: Request[A]): Future[Result] =
    withMaybeRedirectUrl {
      case None => block
      case Some(url) =>
        sessionStoreService.cacheContinueUrl(RedirectUrl(url)).flatMap(_ => block)
    }

  def getUrl(redirectUrlOpt: Option[RedirectUrl])(implicit hc: HeaderCarrier): Future[Option[String]] =
    redirectUrlOpt match {
      case Some(redirectUrl) =>
        checkRedirectUrlAndContinue[Option[String]](redirectUrl, urlOpt => Future successful urlOpt)
      case None => Future successful None
    }

  def getUrl(redirectUrl: RedirectUrl)(implicit hc: HeaderCarrier): Future[Option[String]] =
    checkRedirectUrlAndContinue[Option[String]](redirectUrl, urlOpt => Future successful urlOpt)

}
