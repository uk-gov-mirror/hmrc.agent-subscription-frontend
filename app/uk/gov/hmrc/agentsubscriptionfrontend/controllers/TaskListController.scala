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
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, TaskListService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, Future}

class TaskListController @Inject()(
  override val authConnector: AuthConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  redirectUrlActions: RedirectUrlActions,
  val sessionStoreService: SessionStoreService,
  override val subscriptionJourneyService: SubscriptionJourneyService,
  taskListService: TaskListService)(
  implicit override implicit val appConfig: AppConfig,
  metrics: Metrics,
  override val messagesApi: MessagesApi,
  val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  def showTaskList: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.subscriptionJourneyRecord match {
        case Some(record) => taskListService.createTasks(record).map(tasks => (Ok(html.task_list(tasks))))
        case None         => Future.successful(Redirect(routes.BusinessTypeController.showBusinessTypeForm()))
      }
    }
  }

  def savedProgress(backLink: Option[String] = None): Action[AnyContent] = Action { implicit request =>
    Ok(html.saved_progress(backLink))
  }
}
