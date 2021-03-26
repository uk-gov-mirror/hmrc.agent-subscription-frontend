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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.repository.{SessionCache, SessionCacheRepository}
import uk.gov.hmrc.cache.repository.CacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MongoDBSessionStoreService @Inject()(sessionCache: SessionCacheRepository) {

  implicit val format = Json.format[RedirectUrl]

  val continueUrlCache: SessionCache[RedirectUrl] = new SessionCache[RedirectUrl] {
    override val sessionName: String = "continueUrl"
    override val cacheRepository: CacheRepository = sessionCache
  }

  val goBackUrlCache: SessionCache[String] = new SessionCache[String] {
    override val sessionName: String = "goBackUrl"
    override val cacheRepository: CacheRepository = sessionCache
  }

  val isChangingAnswersCache: SessionCache[Boolean] = new SessionCache[Boolean] {
    override val sessionName: String = "isChangingAnswers"
    override val cacheRepository: CacheRepository = sessionCache
  }

  val agentSessionCache: SessionCache[AgentSession] = new SessionCache[AgentSession] {
    override val sessionName: String = "agentSession"
    override val cacheRepository: CacheRepository = sessionCache
  }

  def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RedirectUrl]] =
    continueUrlCache.fetch

  def cacheContinueUrl(url: RedirectUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    continueUrlCache.save(url).map(_ => ())

  def cacheGoBackUrl(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    goBackUrlCache.save(url).map(_ => ())

  def fetchGoBackUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    goBackUrlCache.fetch

  def cacheIsChangingAnswers(changing: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    isChangingAnswersCache.save(changing).map(_ => ())

  def fetchIsChangingAnswers(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    isChangingAnswersCache.fetch

  def cacheAgentSession(agentSession: AgentSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    agentSessionCache.save(agentSession).map(_ => ())

  def fetchAgentSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentSession]] =
    agentSessionCache.fetch

  def remove()(implicit ec: ExecutionContext): Future[Unit] =
    sessionCache.removeAll().map(_ => ())

}
