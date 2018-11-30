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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, AMLSForm, InitialDetails, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TestSessionStoreService extends SessionStoreService(null) {

  class Session(
    var knownFactsResult: Option[KnownFactsResult] = None,
    var initialDetails: Option[InitialDetails] = None,
    var continueUrl: Option[ContinueUrl] = None,
    var wasEligibleForMapping: Option[Boolean] = None,
    var amlsDetails: Option[AMLSDetails] = None)

  private val sessions = collection.mutable.Map[String, Session]()

  private def sessionKey(implicit hc: HeaderCarrier): String = hc.userId match {
    case None         => "default"
    case Some(userId) => userId.toString
  }

  def currentSession(implicit hc: HeaderCarrier): Session =
    sessions.getOrElseUpdate(sessionKey, new Session())

  def clear(): Unit =
    sessions.clear()

  def allSessionsRemoved: Boolean =
    sessions.isEmpty

  override def fetchKnownFactsResult(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[KnownFactsResult]] =
    Future successful currentSession.knownFactsResult

  override def cacheKnownFactsResult(
    knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.knownFactsResult = Some(knownFactsResult))

  override def fetchInitialDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[InitialDetails]] =
    Future successful currentSession.initialDetails

  override def cacheInitialDetails(
    details: InitialDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.initialDetails = Some(details))

  override def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[ContinueUrl]] =
    Future successful currentSession.continueUrl

  override def cacheContinueUrl(url: ContinueUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.continueUrl = Some(url))

  override def fetchMappingEligible(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    Future successful currentSession.wasEligibleForMapping

  override def cacheMappingEligible(wasEligibleForMapping: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.wasEligibleForMapping = Some(wasEligibleForMapping))

  override def fetchAMLSDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AMLSDetails]] =
    Future.successful(currentSession.amlsDetails)

  override def cacheAMLSDetails(amlsDetails: AMLSDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.amlsDetails = Some(amlsDetails))

  override def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future {
      sessions.remove(sessionKey)
    }
}
