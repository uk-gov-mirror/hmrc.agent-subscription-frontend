/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.{CompletePartialSubscriptionBody, KnownFactsResult, SubscriptionRequestKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService, SubscriptionState}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import scala.concurrent.Future

class StartController @Inject()(
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  knownFactsResultMongoRepository: KnownFactsResultMongoRepository,
  val continueUrlActions: ContinueUrlActions,
  val metrics: Metrics,
  override val appConfig: AppConfig,
  sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService)(implicit val aConfig: AppConfig)
    extends FrontendController with I18nSupport with AuthActions {

  import continueUrlActions._
  import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps._

  val root: Action[AnyContent] = Action.async { implicit request =>
    withMaybeContinueUrl { urlOpt =>
      Future.successful(Redirect(routes.StartController.start().toURLWithParams("continue" -> urlOpt.map(_.url))))
    }
  }

  def start: Action[AnyContent] = Action.async { implicit request =>
    withMaybeContinueUrl { urlOpt =>
      Future.successful(Ok(html.start(urlOpt)))
    }
  }

  val showNonAgentNextSteps: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser {
      Future.successful(Ok(html.non_agent_next_steps()))
    }
  }

  def returnAfterGGCredsCreated(id: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    withMaybeContinueUrlCached {

      val knownFactsResultOpt: Future[Option[KnownFactsResult]] = if (id.isDefined) {
        for {
          knownFactsResultOpt <- knownFactsResultMongoRepository.findKnownFactsResult(id.get)
          _                   <- knownFactsResultMongoRepository.delete(id.get)
        } yield knownFactsResultOpt
      } else Future successful None

      val decideOnSubscription = knownFactsResultOpt.flatMap {
        case Some(knownFacts) =>
          for {
            _                   <- sessionStoreService.cacheKnownFactsResult(knownFacts)
            subscriptionProcess <- subscriptionService.getSubscriptionStatus(knownFacts.utr, knownFacts.postcode)
            isPartiallySubscribed = subscriptionProcess.state == SubscriptionState.SubscribedAndNotEnrolled
            continuedSubscriptionResponse <- if (isPartiallySubscribed)
                                              subscriptionService
                                                .completePartialSubscription(knownFacts.utr, knownFacts.postcode)
                                                .map { arn =>
                                                  mark("Count-Subscription-PartialSubscriptionCompleted")
                                                  Redirect(routes.SubscriptionController.showSubscriptionComplete())
                                                    .flashing("arn" -> arn.value)
                                                } else
                                              Future successful Redirect(
                                                routes.SubscriptionController.showInitialDetails())
          } yield continuedSubscriptionResponse
        case None => Future successful Redirect(routes.CheckAgencyController.showCheckBusinessType())
      }
      decideOnSubscription
    }
  }

  def setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(html.setup_incomplete()))
  }
}
