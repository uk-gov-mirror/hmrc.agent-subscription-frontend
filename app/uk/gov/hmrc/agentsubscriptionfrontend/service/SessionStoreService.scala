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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.RedirectUrlJsonFormat._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionStoreService @Inject()(sessionCache: SessionCache) {

  def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RedirectUrl]] =
    sessionCache.fetchAndGetEntry[RedirectUrl]("continueUrl")

  def cacheContinueUrl(url: RedirectUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("continueUrl", url).map(_ => ())

  def cacheGoBackUrl(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("goBackUrl", url).map(_ => ())

  def fetchGoBackUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    sessionCache.fetchAndGetEntry[String]("goBackUrl")

  def cacheIsChangingAnswers(changing: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("isChangingAnswers", changing).map(_ => ())

  def fetchIsChangingAnswers(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    sessionCache.fetchAndGetEntry[Boolean]("isChangingAnswers")

  def cacheAgentSession(agentSession: AgentSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("agentSession", agentSession).map(_ => ())

  def fetchAgentSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentSession]] =
    sessionCache.fetchAndGetEntry[AgentSession]("agentSession")

  def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.remove().map(_ => ())

}
