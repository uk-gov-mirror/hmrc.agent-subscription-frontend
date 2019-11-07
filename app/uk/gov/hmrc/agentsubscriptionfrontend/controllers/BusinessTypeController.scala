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
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.businessTypeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{business_type}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

class BusinessTypeController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val metrics: Metrics,
  val config: Configuration,
  val env: Environment,
  val subscriptionJourneyService: SubscriptionJourneyService,
  subscriptionService: SubscriptionService,
  mcc: MessagesControllerComponents,
  businessTypeTemplate: business_type
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with AuthActions with SessionBehaviour {

  def redirectToBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm())
  }

  def showBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      redirectUrlActions.withMaybeRedirectUrlCached {
        agent.subscriptionJourneyRecord match {
          case Some(_) => Future.successful(Redirect(routes.TaskListController.showTaskList()))
          case None =>
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(agentSession) =>
                agentSession.businessType match {
                  case Some(businessType) =>
                    Ok(businessTypeTemplate(businessTypeForm.fill(businessType)))
                  case _ => Ok(businessTypeTemplate(businessTypeForm))
                }
              case None => Ok(businessTypeTemplate(businessTypeForm))
            }
        }
      }
    }
  }

  def submitBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessTypeForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(businessTypeTemplate(formWithErrors)),
          validatedBusinessType => {
            sessionStoreService.fetchAgentSession
              .flatMap(_.getOrElse(AgentSession()))
              .flatMap { agentSession =>
                updateSessionAndRedirect(agentSession.copy(businessType = Some(validatedBusinessType)))(
                  routes.UtrController.showUtrForm())
              }
          }
        )
    }
  }

}
