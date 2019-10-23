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

import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.NoSessionException
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders.{RedirectUrl, UnsafePermitAll}

import scala.concurrent.{ExecutionContext, Future}

class TestSessionStoreService extends SessionStoreService(null) {

  class Session(
    var continueUrl: Option[String] = None,
    var goBackUrl: Option[String] = None,
    var changingAnswers: Option[Boolean] = None,
    var agentSession: Option[AgentSession] = None)

  private val sessions = collection.mutable.Map[String, Session]()

  private def sessionKey(implicit hc: HeaderCarrier): String = hc.userId match {
    case None         => "default"
    case Some(userId) => userId.toString
  }

  var currentSessionTest: SessionTest = NormalSession

  def currentSession(implicit hc: HeaderCarrier): Session =
    sessions.getOrElseUpdate(sessionKey, new Session())

  def clear(): Unit = {
    sessions.clear()
    currentSessionTest = NormalSession
  }

  def allSessionsRemoved: Boolean =
    sessions.isEmpty

  override def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RedirectUrl]] =
    fetchFromSession(currentSession.continueUrl.map(c => RedirectUrl(c)))

  override def cacheContinueUrl(url: RedirectUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.continueUrl = Some(url.get(UnsafePermitAll).url))

  override def cacheGoBackUrl(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.goBackUrl =  Some(url))

  private def fetchFromSession[A](property: Option[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[A]] =
    currentSessionTest match {
      case NormalSession => toFuture(property)
      case SessionLost => Future.failed(NoSessionException)
    }

  override def fetchGoBackUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    fetchFromSession(currentSession.goBackUrl)

  override def cacheIsChangingAnswers(changing: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.changingAnswers =  Some(true))

  override def fetchIsChangingAnswers(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    fetchFromSession(currentSession.changingAnswers)

  override def cacheAgentSession(agentSession: AgentSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.agentSession = Some(agentSession))

  override def fetchAgentSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentSession]] =
    fetchFromSession(currentSession.agentSession)

  override def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sessions.remove(sessionKey)
    toFuture(())
  }


}
