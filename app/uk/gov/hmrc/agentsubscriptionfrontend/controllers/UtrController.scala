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
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.{businessTypeForm, utrForm}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

@Singleton
class UtrController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionDataSupport
    with SessionBehaviour {

  def showUtrForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(agentSession) =>
          (agentSession.businessType, agentSession.utr) match {
            case (Some(businessType), Some(utr)) =>
              Ok(html.utr_details(utrForm(businessType.key).fill(utr), businessType))
            case (Some(businessType), None) =>
              Ok(html.utr_details(utrForm(businessType.key), businessType))
            case _ => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
          }
        case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
      }
    }
  }

  def submitUtrForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidBusinessType { businessType =>
        utrForm(businessType.key)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Ok(html.utr_details(formWithErrors, businessType))
            },
            validUtr => {
              sessionStoreService.fetchAgentSession.flatMap {
                case Some(existingSession) =>
                  updateSessionAndRedirect(existingSession.copy(utr = Some(validUtr)))(
                    routes.PostcodeController.showPostcodeForm())
                case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
              }

            }
          )
      }
    }
  }

}
