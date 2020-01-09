/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.ContinueId
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{accessibility_statement, cannot_create_account, not_agent, sign_in_check}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartController @Inject()(
  val authConnector: AuthConnector,
  val redirectUrlActions: RedirectUrlActions,
  val metrics: Metrics,
  val sessionStoreService: SessionStoreService,
  val config: Configuration,
  val env: Environment,
  subscriptionService: SubscriptionService,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  notAgentTemplate: not_agent,
  signInCheckTemplate: sign_in_check,
  cannotCreateAccountTemplate: cannot_create_account,
  accessibilityStatementTemplate: accessibility_statement)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps._

  def root: Action[AnyContent] = Action.async { implicit request =>
    redirectUrlActions.withMaybeRedirectUrl { urlOpt =>
      Redirect(routes.StartController.start().toURLWithParams("continue" -> urlOpt))
    }
  }

  def start: Action[AnyContent] = Action.async { implicit request =>
    redirectUrlActions.withMaybeRedirectUrl { urlOpt =>
      Redirect(
        routes.StartController
          .signInCheck()
          .toURLWithParams("continue" -> urlOpt))
    }
  }

  def showNotAgent: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser {
      Ok(notAgentTemplate())
    }
  }

  def signInCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      redirectUrlActions.withMaybeRedirectUrlCached {
        agent match {
          case hasNonEmptyEnrolments(_) => Ok(signInCheckTemplate())
          case _                        => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
        }
      }
    }
  }

  def returnAfterGGCredsCreated(id: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      redirectUrlActions.withMaybeRedirectUrlCached {
        id match {
          case Some(continueId) =>
            // sanity check - they just came back with a brand new Auth Id
            require(agent.subscriptionJourneyRecord.isEmpty)

            subscriptionService.redirectAfterGGCredsCreatedBasedOnStatus(ContinueId(continueId), agent)

          case None => Future.successful(Redirect(routes.TaskListController.showTaskList()))
        }
      }
    }
  }

  def returnAfterMapping(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      redirectUrlActions.withMaybeRedirectUrlCached {
        subscriptionJourneyService
          .saveJourneyRecord(sjr.copy(mappingComplete = true))
          .map(_ => Redirect(routes.TaskListController.showTaskList()))
      }
    }
  }

  def showCannotCreateAccount: Action[AnyContent] = Action { implicit request =>
    Ok(cannotCreateAccountTemplate())
  }

  def showAccessibilityStatement: Action[AnyContent] = Action { implicit request =>
    Ok(accessibilityStatementTemplate())
  }
}
