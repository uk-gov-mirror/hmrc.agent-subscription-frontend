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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService, SubscriptionState}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartController @Inject()(
  override val authConnector: AuthConnector,
  chainedSessionDetailsRepository: ChainedSessionDetailsRepository,
  continueUrlActions: ContinueUrlActions,
  val sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService)(
  implicit override implicit val appConfig: AppConfig,
  metrics: Metrics,
  override val messagesApi: MessagesApi,
  val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps._

  val root: Action[AnyContent] = Action.async { implicit request =>
    continueUrlActions.withMaybeContinueUrl { urlOpt =>
      Redirect(routes.StartController.start().toURLWithParams("continue" -> urlOpt.map(_.url)))
    }
  }

  def start: Action[AnyContent] = Action.async { implicit request =>
    continueUrlActions.withMaybeContinueUrl { urlOpt =>
      Ok(html.start(urlOpt))
    }
  }

  val showNotAgent: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser {
      Ok(html.not_agent())
    }
  }

  def returnAfterGGCredsCreated(id: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    continueUrlActions.withMaybeContinueUrlCached {
      id match {
        case Some(value) =>
          chainedSessionDetailsRepository.findChainedSessionDetails(value).flatMap {
            case Some(chainedSessionDetails) =>
              chainedSessionDetailsRepository.delete(value).flatMap { _ =>
                storeAgentSessionAndRedirect(chainedSessionDetails.agentSession)
              }

            case None => Redirect(routes.TaskListController.showTaskList())
          }
        case None => Redirect(routes.TaskListController.showTaskList())
      }
    }
  }

  private def storeAgentSessionAndRedirect(
    agentSession: AgentSession)(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] =
    sessionStoreService.cacheAgentSession(agentSession).flatMap { _ =>
      (agentSession.utr, agentSession.postcode) match {
        case (Some(utr), Some(postcode)) =>
          subscriptionService.getSubscriptionStatus(utr, postcode).flatMap { subscriptionProcess =>
            if (subscriptionProcess.state == SubscriptionState.SubscribedButNotEnrolled) {
              handlePartialSubscription(utr, postcode.value, agentSession)
            } else {
              updateTaskListAndContinue
            }
          }
        case _ =>
          sessionStoreService
            .cacheAgentSession(agentSession.copy(taskListFlags = TaskListFlags()))
            .flatMap(_ => toFuture(Redirect(routes.TaskListController.showTaskList())))
      }
    }

  def showCannotCreateAccount: Action[AnyContent] = Action { implicit request =>
    Ok(html.cannot_create_account())
  }

  private def updateTaskListAndContinue(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withValidSession { (_, existingSession) =>
      sessionStoreService
        .cacheAgentSession(
          existingSession
            .copy(taskListFlags = existingSession.taskListFlags.copy(createTaskComplete = true)))
        .flatMap(_ => toFuture(Redirect(routes.TaskListController.showTaskList())))

    }

  private def handlePartialSubscription(kfcUtr: Utr, kfcPostcode: String, agentSession: AgentSession)(
    implicit request: Request[_],
    hc: HeaderCarrier): Future[Result] =
    subscriptionService
      .completePartialSubscription(kfcUtr, Postcode(kfcPostcode))
      .flatMap { _ =>
        mark("Count-Subscription-PartialSubscriptionCompleted")
        sessionStoreService
          .cacheAgentSession(
            agentSession.copy(taskListFlags = agentSession.taskListFlags.copy(createTaskComplete = true)))
          .map(_ => Redirect(routes.SubscriptionController.showSubscriptionComplete()))
      }
}
