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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.{clientDetailsForm, invasiveCheckStartSaAgentCode}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidVariantsTaxPayerOptionForm.{NinoV, UtrV}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessType, ValidVariantsTaxPayerOptionForm}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AssuranceService, SessionStoreService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.invasive_check_start
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AssuranceChecksController @Inject()(
  assuranceService: AssuranceService,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  continueUrlActions: ContinueUrlActions)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def invasiveCheckStart: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        Ok(invasive_check_start(invasiveCheckStartSaAgentCode))
      }
    }
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        invasiveCheckStartSaAgentCode
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Ok(invasive_check_start(formWithErrors))
            },
            correctForm => {
              correctForm.hasSaAgentCode
                .map {
                  case true =>
                    val saAgentCode = correctForm.saAgentCode
                      .getOrElse(throw new IllegalStateException(
                        "Form validation should enforce saAgentCode is always defined if hasSaAgentCode is true"))
                    Redirect(routes.AssuranceChecksController.showClientDetailsForm())
                      .withSession(request.session + ("saAgentReferenceToCheck" -> saAgentCode))

                  case false =>
                    mark("Count-Subscription-InvasiveCheck-Declined")
                    Redirect(routes.StartController.showCannotCreateAccount())
                }
                .getOrElse(throw new IllegalStateException(
                  "InvasiveCheck invasiveCheckStartSaAgentCode.hasSaAgentCode should always be defined"))
            }
          )
      }
    }
  }

  def showClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        Ok(html.client_details(clientDetailsForm))
      }
    }
  }

  def submitClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (businessType, _) =>
        clientDetailsForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.client_details(formWithErrors)),
            correctForm => {
              val retrievedVariant = correctForm.variant
                .getOrElse(throw new IllegalStateException(
                  "Form validation should return error when submitting unavailable variant"))

              def utr: Utr =
                correctForm.utr
                  .flatMap(TaxIdentifierFormatters.normalizeUtr)
                  .getOrElse(throw new Exception("utr should not be empty"))
              def nino: Nino =
                correctForm.nino
                  .flatMap(TaxIdentifierFormatters.normalizeNino)
                  .getOrElse(throw new Exception("nino should not be empty"))

              ValidVariantsTaxPayerOptionForm.withName(retrievedVariant) match {
                case UtrV if Utr.isValid(utr.value) => checkAndRedirect(utr, "utr", businessType)
                case NinoV                          => checkAndRedirect(nino, "nino", businessType)
                case _ =>
                  mark("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
                  Redirect(routes.StartController.showCannotCreateAccount())
              }
            }
          )
      }
    }
  }

  private def checkAndRedirect(value: TaxIdentifier, taxIdentifierName: String, businessType: BusinessType)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent],
    agent: Agent): Future[Result] =
    request.session.get("saAgentReferenceToCheck") match {
      case Some(saAgentReference) =>
        assuranceService
          .checkActiveCesaRelationship(value, taxIdentifierName, SaAgentReference(saAgentReference.toUpperCase))
          .map {
            case true =>
              mark("Count-Subscription-InvasiveCheck-Success")
              //Redirect(getNextPageAfterInvasiveChecks(businessType))
              Redirect(routes.BusinessIdentificationController.showConfirmBusinessForm())
            case false =>
              mark("Count-Subscription-InvasiveCheck-Failed")
              Redirect(routes.StartController.showCannotCreateAccount())
          }
      case None => Redirect(routes.AssuranceChecksController.invasiveCheckStart())
    }

  private def getNextPageAfterInvasiveChecks(businessType: BusinessType) =
    businessType match {
      case SoleTrader | Partnership =>
        routes.NationalInsuranceController.showNationalInsuranceNumberForm()
      case LimitedCompany | Llp =>
        routes.CompanyRegistrationController.showCompanyRegNumberForm()
      case _ => routes.BusinessTypeController.showBusinessTypeForm()
    }
}
