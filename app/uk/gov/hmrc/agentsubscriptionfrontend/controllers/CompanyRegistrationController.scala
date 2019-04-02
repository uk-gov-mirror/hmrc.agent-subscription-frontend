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
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.CompanyRegistrationForms._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

@Singleton
class CompanyRegistrationController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val subscriptionService: SubscriptionService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def showCompanyRegNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.companyRegistrationNumber match {
          case Some(crn) =>
            Ok(html.company_registration(crnForm.fill(crn)))
          case None => Ok(html.company_registration(crnForm))
        }
      }
    }
  }

  def submitCompanyRegNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        crnForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.company_registration(formWithErrors)),
            validCrn =>
              existingSession.utr match {
                case Some(utr) =>
                  subscriptionService.matchCorporationTaxUtrWithCrn(utr, validCrn).flatMap { foundMatch =>
                    if (foundMatch)
                      updateSessionAndRedirect(existingSession.copy(companyRegistrationNumber = Some(validCrn)))(
                        routes.VatDetailsController.showRegisteredForVatForm())
                    else
                      Redirect(routes.BusinessIdentificationController.showNoMatchFound())
                  }
                case _ => Redirect(routes.UtrController.showUtrForm())
            }
          )
      }
    }
  }

}
