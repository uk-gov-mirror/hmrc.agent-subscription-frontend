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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionStoreService @Inject() (sessionCache: SessionCache) {

  def fetchKnownFactsResult(implicit hc: HeaderCarrier): Future[Option[KnownFactsResult]] =
    sessionCache.fetchAndGetEntry[KnownFactsResult]("knownFactsResult")

  def cacheKnownFactsResult(knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("knownFactsResult", knownFactsResult).map(_ => ())

  def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.remove().map(_ => ())

}
