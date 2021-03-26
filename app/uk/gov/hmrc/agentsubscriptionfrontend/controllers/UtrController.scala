/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.utrForm
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.utr_details
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

@Singleton
class UtrController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val sessionStoreService: MongoDBSessionStoreService,
  val config: Configuration,
  val env: Environment,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  utrDetailsTemplate: utr_details)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showUtrForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(agentSession) =>
          (agentSession.businessType, agentSession.utr) match {
            case (Some(businessType), Some(utr)) =>
              Ok(utrDetailsTemplate(utrForm(businessType.key).fill(utr), businessType))
            case (Some(businessType), None) =>
              Ok(utrDetailsTemplate(utrForm(businessType.key), businessType))
            case _ => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
          }
        case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
      }
    }
  }

  def submitUtrForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (businessType, existingSession) =>
        utrForm(businessType.key)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Ok(utrDetailsTemplate(formWithErrors, businessType))
            },
            validUtr => updateSessionAndRedirect(existingSession.copy(utr = Some(validUtr)))(routes.PostcodeController.showPostcodeForm())
          )
      }
    }
  }

}
