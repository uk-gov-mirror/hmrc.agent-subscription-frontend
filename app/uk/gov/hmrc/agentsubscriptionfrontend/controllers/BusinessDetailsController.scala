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
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.{businessDetailsForm, businessTypeForm}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, Postcode, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults.{CheckedInvisibleAssuranceAndFailed, CheckedInvisibleAssuranceAndPassed, ManuallyAssured, RefuseToDealWith}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AssuranceService, SessionStoreService, SubscriptionProcess, SubscriptionService, SubscriptionState}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class BusinessDetailsController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val subscriptionService: SubscriptionService,
  val assuranceService: AssuranceService,
  val auditService: AuditService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  def showBusinessDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      continueUrlActions.withMaybeContinueUrlCached {
        sessionStoreService.fetchAgentSession.map {
          case Some(agentSession) =>
            agentSession.businessType match {
              case Some(businessType) =>
                mark("Count-Subscription-BusinessDetails-Start")
                Ok(html.business_details(businessDetailsForm(businessType.key), businessType))
              case None => Ok(html.business_type(businessTypeForm))
            }

          case None => Ok(html.business_type(businessTypeForm))
        }
      }
    }
  }

  def submitBusinessDetails: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(agentSession) =>
          agentSession.businessType match {
            case Some(businessType) =>
              businessDetailsForm(businessType.key)
                .bindFromRequest()
                .fold(
                  formWithErrors => Ok(html.business_details(formWithErrors, businessType)),
                  businessDetails =>
                    if (Utr.isValid(businessDetails.utr.value)) {
                      checkSubscriptionStatusAndUpdateSession(
                        businessDetails.utr,
                        Postcode(businessDetails.postcode),
                        agentSession).map { resultWithSession =>
                        val sessionData = (request.session.data ++ resultWithSession.session.data.toSeq) + ("businessType" -> businessType.key)
                        resultWithSession.withSession(sessionData.toSeq: _*)
                      }
                    } else {
                      mark("Count-Subscription-NoAgencyFound")
                      Redirect(routes.BusinessIdentificationController.showNoMatchFound())
                  }
                )
            case None => Ok(html.business_type(businessTypeForm))
          }

        case None => Ok(html.business_type(businessTypeForm))

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
                agentSession.copy(postcode = Some(postcode), utr = Some(utr), registration = Some(reg)))
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
            updateSessionAndRedirect(
              agentSession.copy(postcode = Some(postcode), utr = Some(utr), registration = Some(registration)))(
              routes.InvasiveChecksController.invasiveCheckStart())
          }
      case maybeAssured @ (None | ManuallyAssured(_) | CheckedInvisibleAssuranceAndPassed(_)) =>
        maybeAssured match {
          case Some(assuranceResults) =>
            auditService
              .sendAgentAssuranceAuditEvent(utr, postcode, assuranceResults)
              .flatMap { _ =>
                updateSessionAndRedirect(
                  agentSession.copy(postcode = Some(postcode), utr = Some(utr), registration = Some(registration)))(
                  routes.BusinessIdentificationController.showConfirmBusinessForm())
              }
          case None =>
            updateSessionAndRedirect(
              agentSession.copy(postcode = Some(postcode), utr = Some(utr), registration = Some(registration)))(
              routes.BusinessIdentificationController.showConfirmBusinessForm())
        }
    }

}
