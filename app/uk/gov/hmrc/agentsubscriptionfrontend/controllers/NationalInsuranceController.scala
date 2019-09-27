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
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.ninoForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters.normalizeNino
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NationalInsuranceController @Inject()(
  override val redirectUrlActions: RedirectUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  override val subscriptionJourneyService: SubscriptionJourneyService,
  val subscriptionService: SubscriptionService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  /**
    * In-case of SoleTrader or Partnerships, we should display NI and DOB pages based on if nino and dob exist or not.
    * We need to force users to go through these pages, hence the below checks
    */
  def showNationalInsuranceNumberForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        withValidBusinessType(existingSession) {
          agent.authNino match {
            case Some(_) =>
              existingSession.nino match {
                case Some(nino) =>
                  Ok(html.national_insurance_number(ninoForm.fill(nino)))
                case None => Ok(html.national_insurance_number(ninoForm))
              }
            case None => Redirect(routes.VatDetailsController.showRegisteredForVatForm())
          }
        }
      }
    }
  }

  private def withValidBusinessType(agentSession: AgentSession)(result: Result)(
    implicit hc: HeaderCarrier): Future[Result] =
    agentSession.businessType match {
      case Some(SoleTrader | Partnership) => Future.successful(result)
      case _ =>
        updateSessionAndRedirect(AgentSession())(routes.BusinessTypeController.showBusinessTypeForm())
    }

  def submitNationalInsuranceNumberForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        ninoForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.national_insurance_number(formWithErrors)),
            validNino => {
              def ninosMatched = agent.authNino.flatMap(normalizeNino) == normalizeNino(validNino.value)
              if (ninosMatched) {
                subscriptionService
                  .getDesignatoryDetails(validNino)
                  .map(_.person.flatMap(_.dateOfBirth))
                  .flatMap {
                    case Some(dateOfBirth) =>
                      updateSessionAndRedirect(
                        existingSession.copy(nino = Some(validNino), dateOfBirthFromCid = Some(dateOfBirth)))(
                        routes.DateOfBirthController.showDateOfBirthForm())
                    case None =>
                      Logger.warn("no DateOfBirth in the /citizen-details response for logged in agent")
                      updateSessionAndRedirect(existingSession.copy(nino = Some(validNino)))(
                        routes.VatDetailsController.showRegisteredForVatForm())
                  }
              } else {
                Future.successful(Redirect(routes.BusinessIdentificationController.showNoMatchFound()))
              }
            }
          )
      }
    }
  }
}
