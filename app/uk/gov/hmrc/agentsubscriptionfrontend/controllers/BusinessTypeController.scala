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
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.businessTypeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

class BusinessTypeController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def redirectToBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    Redirect(routes.BusinessTypeController.showBusinessTypeForm())
  }

  def showBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      continueUrlActions.withMaybeContinueUrlCached {
        sessionStoreService.fetchAgentSession.flatMap {
          case Some(agentSession) =>
            agentSession.businessType match {
              case Some(businessType) =>
                Ok(html.business_type(businessTypeForm.fill(businessType)))
              case None => Ok(html.business_type(businessTypeForm))
            }
          case None => Ok(html.business_type(businessTypeForm))
        }
      }
    }
  }

  def submitBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessTypeForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(html.business_type(formWithErrors)),
          validatedBusinessType => {
            sessionStoreService.fetchAgentSession
              .flatMap(_.getOrElse(AgentSession()))
              .flatMap { agentSession =>
                updateSessionAndRedirect(agentSession.copy(businessType = Some(validatedBusinessType)))(
                  routes.BusinessDetailsController.showBusinessDetailsForm())
              }
          }
        )
    }
  }

}
