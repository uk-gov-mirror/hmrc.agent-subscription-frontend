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
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.NoSessionException

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignedOutController @Inject()(
  val sessionStoreService: SessionStoreService,
  override val redirectUrlActions: RedirectUrlActions,
  override val authConnector: AuthConnector,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  def redirectUserToCreateCleanCreds: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      for {
        agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
        redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
        continueId = {
          agent.subscriptionJourneyRecord.flatMap(_.continueId)
        }
      } yield {
        val continueUrl =
          addParamsToUrl(
            appConfig.rootContinueUrl,
            "id"       -> continueId,
            "continue" -> redirectUrl
          )

        SeeOther(addParamsToUrl(appConfig.ggRegistrationFrontendExternalUrl, "continue" -> Some(continueUrl))).withNewSession
      }
    }
  }

  def signOutWithContinueUrl: Action[AnyContent] = Action.async { implicit request =>
    val result: Future[Result] = for {
      agentSubContinueUrlOpt <- sessionStoreService.fetchContinueUrl
      redirectUrl            <- redirectUrlActions.getUrl(agentSubContinueUrlOpt)
    } yield {
      val signOutUrlWithContinueUrl =
        addParamsToUrl(appConfig.companyAuthSignInUrl, "continue" -> redirectUrl)
      SeeOther(signOutUrlWithContinueUrl).withNewSession
    }

    result.recover {
      case NoSessionException => startNewSession
    }
  }

  def startSurvey: Action[AnyContent] = Action { implicit request =>
    SeeOther(appConfig.surveyRedirectUrl).withNewSession
  }

  def redirectToASAccountPage: Action[AnyContent] = Action { implicit request =>
    SeeOther(appConfig.agentServicesAccountUrl).withNewSession
  }

  def signOut: Action[AnyContent] = Action {
    startNewSession
  }

  private def startNewSession: Result =
    Redirect(routes.StartController.start()).withNewSession

  def redirectToBusinessTypeForm: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm()).withNewSession
  }
}
