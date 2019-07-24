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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import cats.data.OptionT
import cats.instances.future._
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.ChainedSessionDetails
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.agentsubscriptionfrontend.repository.StashedChainedSessionDetails.StashedChainnedSessionId
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignedOutController @Inject()(
  chainedSessionRepository: ChainedSessionDetailsRepository,
  val sessionStoreService: SessionStoreService,
  continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def redirectToSos = Action.async { implicit request =>
    for {
      chainedSessionIdOpt    <- prepareChainedSession()
      agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
    } yield {
      val rootContinueUrl: String =
        if (appConfig.isDevMode) "http://localhost:9437/agent-subscription/return-after-gg-creds-created"
        else "/agent-subscription/return-after-gg-creds-created"
      val continueUrl =
        addParamsToUrl(
          rootContinueUrl,
          "id"       -> chainedSessionIdOpt.map(_.toString),
          "continue" -> agentSubContinueUrlOpt.map(_.url)
        )
      SeeOther(addParamsToUrl(appConfig.sosRedirectUrl, "continue" -> Some(continueUrl))).withNewSession
    }
  }

  private def prepareChainedSession()(implicit hc: HeaderCarrier): Future[Option[StashedChainnedSessionId]] =
    (for {
      agentSession <- OptionT(sessionStoreService.fetchAgentSession)
      id <- OptionT(
             chainedSessionRepository
               .create(ChainedSessionDetails(agentSession))
               .map(id => Option(id)))
    } yield id).value

  def signOutWithContinueUrl = Action.async { implicit request =>
    sessionStoreService.fetchContinueUrl.map { maybeContinueUrl =>
      val signOutUrlWithContinueUrl =
        addParamsToUrl(appConfig.companyAuthSignInUrl, "continue" -> maybeContinueUrl.map(_.url))
      SeeOther(signOutUrlWithContinueUrl).withNewSession
    }
  }

  def startSurvey = Action { implicit request =>
    SeeOther(appConfig.surveyRedirectUrl).withNewSession
  }

  def redirectToASAccountPage = Action { implicit request =>
    SeeOther(appConfig.agentServicesAccountUrl).withNewSession
  }

  def signOut: Action[AnyContent] = Action {
    Redirect(routes.StartController.start()).withNewSession
  }

  def redirectToBusinessTypeForm = Action { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm()).withNewSession
  }
}
