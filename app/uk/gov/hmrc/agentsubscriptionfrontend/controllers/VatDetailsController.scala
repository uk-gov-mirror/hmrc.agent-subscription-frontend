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
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.VatDetailsController.registeredForVatForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessType, RadioInputAnswer, RegisteredForVat}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators.radioInputSelected
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

@Singleton
class VatDetailsController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionDataSupport
    with SessionBehaviour {

  def showRegisteredForVatForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidBusinessType { businessType =>
        Ok(html.registered_for_vat(registeredForVatForm, getBackLink(businessType)))
      }
    }
  }

  def submitRegisteredForVatForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidBusinessType { businessType =>
        registeredForVatForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.registered_for_vat(formWithErrors, getBackLink(businessType))),
            choice => {
              sessionStoreService.fetchAgentSession.flatMap {
                case Some(existingSession) =>
                  updateSessionAndRedirectToNextPage(
                    existingSession.copy(registeredForVat = Some(choice.confirm == Yes)))
                case None => Redirect(routes.BusinessIdentificationController.showBusinessTypeForm())
              }
            }
          )
      }
    }
  }

  def showVatDeatilsForm: Action[AnyContent] = ???

  def submitVatDeatilsForm: Action[AnyContent] = ???

  private def getBackLink(businessType: BusinessType) =
    if (businessType == SoleTrader || businessType == Partnership) {
      routes.DateOfBirthController.showDateOfBirthForm().url
    } else {
      routes.BusinessIdentificationController.showCompanyRegNumberForm().url
    }
}

object VatDetailsController {

  val registeredForVatForm: Form[RegisteredForVat] =
    Form[RegisteredForVat](
      mapping("registeredForVat" -> optional(text).verifying(
        radioInputSelected("registered-for-vat.error.no-radio-selected")))(answer =>
        RegisteredForVat(RadioInputAnswer.apply(answer.getOrElse(""))))(answer =>
        Some(RadioInputAnswer.unapply(answer.confirm)))
        .verifying(
          "registered-for-vat.confirm-business-value.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.confirm)))

}
