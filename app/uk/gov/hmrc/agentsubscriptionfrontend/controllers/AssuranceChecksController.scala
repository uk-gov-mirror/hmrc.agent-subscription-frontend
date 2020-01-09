/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.{Configuration, Environment}
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.{clientDetailsForm, invasiveCheckStartSaAgentCodeForm}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessType, TaxPayerNino, TaxPayerUtr, ValidVariantsTaxPayerOptionForm}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AssuranceService, SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{client_details, invasive_check_start}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssuranceChecksController @Inject()(
  val config: Configuration,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val redirectUrlActions: RedirectUrlActions,
  val subscriptionJourneyService: SubscriptionJourneyService,
  val sessionStoreService: SessionStoreService,
  assuranceService: AssuranceService,
  invasiveCheckStartTemplate: invasive_check_start,
  clientDetailsTemplate: client_details,
  mcc: MessagesControllerComponents)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def invasiveCheckStart: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        Ok(invasiveCheckStartTemplate(invasiveCheckStartSaAgentCodeForm))
      }
    }
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        invasiveCheckStartSaAgentCodeForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Ok(invasiveCheckStartTemplate(formWithErrors))
            },
            correctForm => {
              if (correctForm.hasSaAgentCode) {
                Redirect(routes.AssuranceChecksController.showClientDetailsForm())
                  .withSession(request.session + ("saAgentReferenceToCheck" -> correctForm.saAgentCode))
              } else {
                mark("Count-Subscription-InvasiveCheck-Declined")
                Redirect(routes.StartController.showCannotCreateAccount())
              }
            }
          )
      }
    }
  }

  def showClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, _) =>
        Ok(clientDetailsTemplate(clientDetailsForm))
      }
    }
  }

  def submitClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (businessType, _) =>
        clientDetailsForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(clientDetailsTemplate(formWithErrors)),
            correctForm => {
              ValidVariantsTaxPayerOptionForm.findByValue(correctForm.variant) match {
                case TaxPayerUtr  => checkAndRedirect(Utr(correctForm.utr), "utr", businessType)
                case TaxPayerNino => checkAndRedirect(Nino(correctForm.nino), "nino", businessType)
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
              Redirect(getNextPageAfterInvasiveChecks(businessType))
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
