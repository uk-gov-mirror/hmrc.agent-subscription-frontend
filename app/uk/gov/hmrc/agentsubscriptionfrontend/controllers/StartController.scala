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
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.IsEligible
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, MappingEligibility, Postcode, TaskListFlags}
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
                storeAgentSessionAndRedirect(
                  chainedSessionDetails.agentSession,
                  chainedSessionDetails.wasEligibleForMapping)
              }

            case None => Redirect(routes.TaskListController.showTaskList())
          }
        case None => Redirect(routes.TaskListController.showTaskList())
      }
    }
  }

  private def storeAgentSessionAndRedirect(agentSession: AgentSession, wasEligibleForMapping: Option[Boolean])(
    implicit hc: HeaderCarrier,
    request: Request[_]): Future[Result] =
    sessionStoreService.cacheAgentSession(agentSession).flatMap { _ =>
      val result = if (appConfig.autoMapAgentEnrolments) {
        sessionStoreService.cacheMappingEligible(
          wasEligibleForMapping.getOrElse {
            Logger.warn("chainedSessionDetails did not cache wasEligibleForMapping")
            false
          }
        )
      } else toFuture(())

      result.flatMap { _ =>
        (agentSession.utr, agentSession.postcode) match {
          case (Some(utr), Some(postcode)) =>
            subscriptionService.getSubscriptionStatus(utr, postcode).flatMap { subscriptionProcess =>
              if (subscriptionProcess.state == SubscriptionState.SubscribedButNotEnrolled) {
                handlePartialSubscription(utr, postcode.value, wasEligibleForMapping)
              } else {
                handleAutoMapping(wasEligibleForMapping)
              }
            }
          case _ =>
            sessionStoreService
              .cacheAgentSession(agentSession.copy(taskListFlags = TaskListFlags()))
              .flatMap(_ => toFuture(Redirect(routes.TaskListController.showTaskList())))
        }
      }
    }

  def showCannotCreateAccount: Action[AnyContent] = Action { implicit request =>
    Ok(html.cannot_create_account())
  }

  private def handleAutoMapping(
    eligibleForMapping: Option[Boolean])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    withValidSession { (_, existingSession) =>
      MappingEligibility.apply(eligibleForMapping) match {
        case IsEligible if appConfig.autoMapAgentEnrolments =>
          sessionStoreService
            .cacheAgentSession(
              existingSession
                .copy(taskListFlags = existingSession.taskListFlags.copy(createTaskComplete = true)))
            .flatMap(_ =>
              sessionStoreService.fetchContinueUrl.flatMap {
                case Some(_) => toFuture(Redirect(routes.SubscriptionController.showLinkClients()))
                case None    => toFuture(Redirect(routes.TaskListController.showTaskList()))
            })
        case _ =>
          sessionStoreService
            .cacheAgentSession(
              existingSession
                .copy(taskListFlags = existingSession.taskListFlags.copy(createTaskComplete = true)))
            .flatMap(_ =>
              sessionStoreService.fetchContinueUrl.flatMap {
                case Some(_) => toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
                case None    => toFuture(Redirect(routes.TaskListController.showTaskList()))
            })
      }
    }

  private def handlePartialSubscription(kfcUtr: Utr, kfcPostcode: String, eligibleForMapping: Option[Boolean])(
    implicit request: Request[_],
    hc: HeaderCarrier): Future[Result] =
    MappingEligibility.apply(eligibleForMapping) match {
      case IsEligible =>
        Redirect(routes.SubscriptionController.showLinkClients())
          .withSession(request.session + ("isPartiallySubscribed" -> "true"))
      case _ =>
        subscriptionService
          .completePartialSubscription(kfcUtr, Postcode(kfcPostcode))
          .map { _ =>
            mark("Count-Subscription-PartialSubscriptionCompleted")
            Redirect(routes.SubscriptionController.showSubscriptionComplete())
          }
    }

}
