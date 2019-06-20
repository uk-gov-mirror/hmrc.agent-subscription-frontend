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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.postcodeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PostcodeController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService,
  assuranceService: AssuranceService,
  auditService: AuditService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def showPostcodeForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        existingSession.postcode match {
          case Some(postcode) =>
            Ok(html.postcode(postcodeForm.fill(postcode)))
          case None => Ok(html.postcode(postcodeForm))
        }
      }
    }
  }

  def submitPostcodeForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidSession { (_, existingSession) =>
        postcodeForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.postcode(formWithErrors)),
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
      case SubscriptionProcess(SubscriptionState.Unsubscribed, Some(registrationDetails)) =>
        checkAssuranceAndUpdateSession(utr, postcode, registrationDetails, agentSession)

      case SubscriptionProcess(SubscriptionState.SubscribedButNotEnrolled, Some(reg)) =>
        for {
          _ <- sessionStoreService.cacheAgentSession(
                agentSession.copy(postcode = Some(postcode), registration = Some(reg)))
          result <- withCleanCreds(agent) {
                     subscriptionService
                       .completePartialSubscription(utr, postcode)
                       .map { _ =>
                         mark("Count-Subscription-PartialSubscriptionCompleted")
                         Redirect(routes.SubscriptionController.showSubscriptionComplete())
                       }
                   }
        } yield result

      case SubscriptionProcess(SubscriptionState.SubscribedAndEnrolled, _) =>
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
                  agentSession.copy(postcode = Some(postcode), registration = Some(registration)))(
                  getNextPage(agentSession.businessType))
              }
          case None =>
            updateSessionAndRedirect(agentSession.copy(postcode = Some(postcode), registration = Some(registration)))(
              getNextPage(agentSession.businessType))
        }
    }

  private def getNextPage(businessType: Option[BusinessType]) =
    businessType match {
      case Some(bt) =>
        if (bt == SoleTrader || bt == Partnership) {
          routes.NationalInsuranceController.showNationalInsuranceNumberForm()
        } else {
          routes.CompanyRegistrationController.showCompanyRegNumberForm()
        }

      case None => routes.BusinessTypeController.showBusinessTypeForm()
    }
}
