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
import uk.gov.hmrc.agentsubscriptionfrontend.models.ContinueId
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartController @Inject()(
  override val authConnector: AuthConnector,
  redirectUrlActions: RedirectUrlActions,
  val sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService,
  subscriptionJourneyService: SubscriptionJourneyService)(
  implicit override implicit val appConfig: AppConfig,
  metrics: Metrics,
  override val messagesApi: MessagesApi,
  val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps._

  def root: Action[AnyContent] = Action.async { implicit request =>
    redirectUrlActions.withMaybeRedirectUrl { urlOpt =>
      Redirect(routes.StartController.start().toURLWithParams("continue" -> urlOpt))
    }
  }

  def start: Action[AnyContent] = Action.async { implicit request =>
    redirectUrlActions.withMaybeRedirectUrl { urlOpt =>
      val nextUrl: String = routes.BusinessTypeController
        .showBusinessTypeForm()
        .toURLWithParams("continue" -> urlOpt)
      Ok(html.start(nextUrl))
    }
  }

  def showNotAgent: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser {
      Ok(html.not_agent())
    }
  }

  def returnAfterGGCredsCreated(id: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      redirectUrlActions.withMaybeRedirectUrlCached {
        id match {
          case Some(continueId) =>
            // sanity check - they just came back with a brand new Auth Id
            require(agent.subscriptionJourneyRecord.isEmpty)

            for {
              record <- subscriptionJourneyService.getMandatoryJourneyRecord(ContinueId(continueId))
              _ <- subscriptionJourneyService.saveJourneyRecord(
                    record.copy(cleanCredsAuthProviderId = Some(agent.authProviderId)))
            } yield Redirect(routes.TaskListController.showTaskList())

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
    Ok(html.cannot_create_account())
  }
}
