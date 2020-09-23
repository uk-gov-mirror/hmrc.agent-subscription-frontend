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
import play.api.{Configuration, Environment, Logger}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.postcodeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.postcode
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PostcodeController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val env: Environment,
  val config: Configuration,
  subscriptionService: SubscriptionService,
  val metrics: Metrics,
  assuranceService: AssuranceService,
  auditService: AuditService,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  postcodeTemplate: postcode)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showPostcodeForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (businessType, existingSession) =>
        existingSession.postcode match {
          case Some(postcode) =>
            Ok(postcodeTemplate(postcodeForm.fill(postcode), businessType))
          case None => Ok(postcodeTemplate(postcodeForm, businessType))
        }
      }
    }
  }

  def submitPostcodeForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (businessType, existingSession) =>
        postcodeForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(postcodeTemplate(formWithErrors, businessType)),
            validPostcode => {
              existingSession.utr match {
                case Some(utr) => checkSubscriptionStatusAndUpdateSession(utr, validPostcode, existingSession)
                case None      => Redirect(routes.UtrController.showUtrForm())
              }
            }
          )
      }
    }
  }

  private def checkSubscriptionStatusAndUpdateSession(utr: Utr, postcode: Postcode, agentSession: AgentSession)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent],
    agent: Agent): Future[Result] =
    subscriptionService.getSubscriptionStatus(utr, postcode).flatMap {
      case SubscriptionProcess(subscriptionState, Some(registrationDetails))
          if subscriptionState == Unsubscribed || subscriptionState == SubscribedButNotEnrolled =>
        checkAssuranceAndUpdateSession(utr, postcode, registrationDetails, agentSession)

      case SubscriptionProcess(SubscribedAndEnrolled, _) =>
        mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
        Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())

      case _ =>
        mark("Count-Subscription-NoAgencyFound")
        Redirect(routes.BusinessIdentificationController.showNoMatchFound())
    }

  private def checkAssuranceAndUpdateSession(
    utr: Utr,
    postcode: Postcode,
    registration: Registration,
    agentSession: AgentSession)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent],
    agent: Agent): Future[Result] =
    assuranceService.assureIsAgent(utr).flatMap {
      case RefuseToDealWith(_) =>
        Redirect(routes.StartController.showCannotCreateAccount())
      case CheckedInvisibleAssuranceAndFailed(assuranceResults) =>
        auditService
          .sendAgentAssuranceAuditEvent(utr, postcode, assuranceResults)
          .flatMap { _ =>
            mark("Count-Subscription-InvasiveCheck-Start")
            updateSessionAndRedirect(agentSession.copy(postcode = Some(postcode), registration = Some(registration)))(
              routes.AssuranceChecksController.invasiveCheckStart())
          }
      case maybeAssured @ (None | ManuallyAssured(_) | CheckedInvisibleAssuranceAndPassed(_)) =>
        maybeAssured match {
          case Some(assuranceResults) =>
            auditService
              .sendAgentAssuranceAuditEvent(utr, postcode, assuranceResults)
              .flatMap { _ =>
                updateSessionAndRedirect(
                  agentSession.copy(
                    postcode = Some(postcode),
                    registration = Some(registration),
                    isMAA = Some(assuranceResults.isManuallyAssured)))(
                  getNextPage(agentSession.businessType, maybeAssured))
              }
          case None =>
            updateSessionAndRedirect(agentSession.copy(postcode = Some(postcode), registration = Some(registration)))(
              getNextPage(agentSession.businessType, maybeAssured))
        }
    }

  private def getNextPage(businessType: Option[BusinessType], assuranceResults: Option[AssuranceResults])(
    implicit agent: Agent) =
    businessType match {
      case Some(bt) =>
        if (bt == SoleTrader || bt == Partnership) {
          agent.authNino match {
            case Some(_) => routes.NationalInsuranceController.showNationalInsuranceNumberForm()
            case None =>
              Logger.warn("NINO doesn't exist for logged in agent")
              routes.VatDetailsController.showRegisteredForVatForm()
          }
        } else {
          assuranceResults.fold(routes.CompanyRegistrationController.showCompanyRegNumberForm()) { ar =>
            if (ar.isManuallyAssured && bt == Llp) {
              Logger(getClass).warn(s"Manually assured agent with business type LLP - skipping Companies House check")
              routes.BusinessIdentificationController.showConfirmBusinessForm()
            } else routes.CompanyRegistrationController.showCompanyRegNumberForm()
          }

        }

      case None => routes.BusinessTypeController.showBusinessTypeForm()
    }
}
