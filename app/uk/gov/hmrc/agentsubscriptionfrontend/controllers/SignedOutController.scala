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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

@Singleton
class SignedOutController @Inject()(
  val sessionStoreService: SessionStoreService,
  continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  def redirectToSos: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      for {
        agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
        continueId = agent.getMandatorySubscriptionRecord.continueId
      } yield {
        val rootContinueUrl: String =
          if (appConfig.isDevMode) "http://localhost:9437/agent-subscription/return-after-gg-creds-created"
          else "/agent-subscription/return-after-gg-creds-created"
        val continueUrl =
          addParamsToUrl(
            rootContinueUrl,
            "id"       -> continueId,
            "continue" -> agentSubContinueUrlOpt.map(_.url)
          )
        SeeOther(addParamsToUrl(appConfig.sosRedirectUrl, "continue" -> Some(continueUrl))).withNewSession
      }
    }
  }

  def signOutWithContinueUrl: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService.fetchContinueUrl.map { maybeContinueUrl =>
      val signOutUrlWithContinueUrl =
        addParamsToUrl(appConfig.companyAuthSignInUrl, "continue" -> maybeContinueUrl.map(_.url))
      SeeOther(signOutUrlWithContinueUrl).withNewSession
    }
  }

  def startSurvey: Action[AnyContent] = Action { implicit request =>
    SeeOther(appConfig.surveyRedirectUrl).withNewSession
  }

  def redirectToASAccountPage: Action[AnyContent] = Action { implicit request =>
    SeeOther(appConfig.agentServicesAccountUrl).withNewSession
  }

  def signOut: Action[AnyContent] = Action {
    Redirect(routes.StartController.start()).withNewSession
  }

  def redirectToBusinessTypeForm: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm()).withNewSession
  }
}
