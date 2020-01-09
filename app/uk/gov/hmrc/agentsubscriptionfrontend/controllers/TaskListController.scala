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
import javax.inject.Inject
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, TaskListService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{saved_progress, task_list}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

class TaskListController @Inject()(
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val env: Environment,
  val config: Configuration,
  agentAssuranceConnector: AgentAssuranceConnector,
  val redirectUrlActions: RedirectUrlActions,
  val sessionStoreService: SessionStoreService,
  val subscriptionJourneyService: SubscriptionJourneyService,
  taskListService: TaskListService,
  mcc: MessagesControllerComponents,
  savedProgressTemplate: saved_progress,
  taskListTemplate: task_list)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showTaskList: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.subscriptionJourneyRecord match {
        case Some(record) => taskListService.createTasks(record).map(tasks => (Ok(taskListTemplate(tasks))))
        case None         => Future.successful(Redirect(routes.BusinessTypeController.showBusinessTypeForm()))
      }
    }
  }

  def savedProgress(backLink: Option[String] = None): Action[AnyContent] = Action { implicit request =>
    Ok(savedProgressTemplate(backLink))
  }
}
