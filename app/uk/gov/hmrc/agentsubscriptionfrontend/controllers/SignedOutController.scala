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
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.timed_out
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.NoSessionException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignedOutController @Inject()(
  timedOutTemplate: timed_out,
  val sessionStoreService: SessionStoreService,
  val redirectUrlActions: RedirectUrlActions,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

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

  def keepAlive: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok("OK")
  }

  def timedOut: Action[AnyContent] = Action.async { implicit request =>
    Future successful Forbidden(timedOutTemplate())
  }

  private def startNewSession: Result =
    Redirect(routes.TaskListController.showTaskList()).withNewSession

  def redirectToBusinessTypeForm: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm()).withNewSession
  }
}
