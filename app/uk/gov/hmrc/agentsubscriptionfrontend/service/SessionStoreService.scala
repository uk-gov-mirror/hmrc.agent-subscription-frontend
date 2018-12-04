/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.agentsubscriptionfrontend.models.ContinueUrlJsonFormat._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, InitialDetails, KnownFactsResult}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionStoreService @Inject()(sessionCache: SessionCache) {

  def fetchKnownFactsResult(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[KnownFactsResult]] =
    sessionCache.fetchAndGetEntry[KnownFactsResult]("knownFactsResult")

  def cacheKnownFactsResult(
    knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("knownFactsResult", knownFactsResult).map(_ => ())

  def fetchInitialDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[InitialDetails]] =
    sessionCache.fetchAndGetEntry[InitialDetails]("initialDetails")

  def cacheInitialDetails(details: InitialDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("initialDetails", details).map(_ => ())

  def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[ContinueUrl]] =
    sessionCache.fetchAndGetEntry[ContinueUrl]("continueUrl")

  def cacheContinueUrl(url: ContinueUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("continueUrl", url).map(_ => ())

  def fetchMappingEligible(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    sessionCache.fetchAndGetEntry[Boolean]("mappingEligible")

  def cacheMappingEligible(
    wasEligibleForMapping: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("mappingEligible", wasEligibleForMapping).map(_ => ())

  def fetchAMLSDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AMLSDetails]] =
    sessionCache.fetchAndGetEntry[AMLSDetails]("amlsDetails")

  def cacheAMLSDetails(amlsDetails: AMLSDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("amlsDetails", amlsDetails).map(_ => ())

  def cacheGoBackUrl(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.cache("goBackUrl", url).map(_ => ())

  def fetchGoBackUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    sessionCache.fetchAndGetEntry[String]("goBackUrl")

  def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sessionCache.remove().map(_ => ())

}
