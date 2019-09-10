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

  def currentSession(implicit hc: HeaderCarrier): Session =
    sessions.getOrElseUpdate(sessionKey, new Session())

  def clear(): Unit =
    sessions.clear()

  def allSessionsRemoved: Boolean =
    sessions.isEmpty

  override def fetchContinueUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RedirectUrl]] =
    Future successful currentSession.continueUrl.map(c => RedirectUrl(c))

  override def cacheContinueUrl(url: RedirectUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(currentSession.continueUrl = Some(url.get(UnsafePermitAll).url))

  override def cacheGoBackUrl(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.goBackUrl =  Some(url))

  override def fetchGoBackUrl(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    toFuture(currentSession.goBackUrl)

  override def cacheIsChangingAnswers(changing: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.changingAnswers =  Some(true))

  override def fetchIsChangingAnswers(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    toFuture(currentSession.changingAnswers)

  override def cacheAgentSession(agentSession: AgentSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    toFuture(currentSession.agentSession = Some(agentSession))

  override def fetchAgentSession(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentSession]] =
    toFuture(currentSession.agentSession)

  override def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sessions.remove(sessionKey)
    toFuture(())
  }


}
