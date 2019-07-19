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
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.amls._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AMLSController @Inject()(
  override val authConnector: AuthConnector,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val continueUrlActions: ContinueUrlActions,
  val sessionStoreService: SessionStoreService,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  def showCheckAmlsPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          existingSession.checkAmls.fold(
            Ok(check_amls(checkAmlsForm, existingSession.taskListFlags.businessTaskComplete)))(
            amls =>
              Ok(
                check_amls(
                  checkAmlsForm.bind(Map("registeredAmls" -> amls.toString)),
                  existingSession.taskListFlags.businessTaskComplete)))
        }
      }
    }
  }

  def submitCheckAmls: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          checkAmlsForm
            .bindFromRequest()
            .fold(
              formWithErrors =>
                Ok(html.amls.check_amls(formWithErrors, existingSession.taskListFlags.businessTaskComplete)),
              validForm => {
                val nextPage = validForm match {
                  case Yes =>
                    Redirect(routes.AMLSController.showAmlsDetailsForm())
                  case No => Redirect(routes.AMLSController.showCheckAmlsAlreadyAppliedForm())
                }
                sessionStoreService
                  .cacheAgentSession(existingSession.copy(checkAmls = RadioInputAnswer.unapply(validForm)))
                  .map(_ => nextPage)
              }
            )
        }
      }
    }
  }

  def showCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          existingSession.amlsAppliedFor.fold(Ok(amls_applied_for(appliedForAmlsForm)))(amls =>
            Ok(amls_applied_for(appliedForAmlsForm.bind(Map("amlsAppliedFor" -> amls.toString)))))
        }
      }
    }
  }

  def submitCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          appliedForAmlsForm.bindFromRequest.fold(
            formWithErrors => Ok(amls_applied_for(formWithErrors)),
            validForm => {
              val nextPage = validForm match {
                case Yes => Redirect(routes.AMLSController.showAmlsApplicationDatePage())
                case No  => Redirect(routes.AMLSController.showAmlsNotAppliedPage())
              }
              sessionStoreService
                .cacheAgentSession(existingSession.copy(amlsAppliedFor = RadioInputAnswer.unapply(validForm)))
                .map(_ => nextPage)
            }
          )
        }
      }
    }
  }

  val showAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          for {
            cachedAmlsDetails <- existingSession.map(_.amlsDetails)
            cachedGoBackUrl   <- sessionStoreService.fetchGoBackUrl
          } yield {
            (cachedAmlsDetails, cachedGoBackUrl) match {
              case (Some(amlsDetails), mayBeGoBackUrl) =>
                amlsDetails.details match {
                  case Right(registeredDetails) =>
                    val form: Map[String, String] = Map(
                      "amlsCode"         -> amlsBodies.find(_._2 == amlsDetails.supervisoryBody).map(_._1).getOrElse(""),
                      "membershipNumber" -> registeredDetails.membershipNumber,
                      "expiry.day"       -> registeredDetails.membershipExpiresOn.getDayOfMonth.toString,
                      "expiry.month"     -> registeredDetails.membershipExpiresOn.getMonthValue.toString,
                      "expiry.year"      -> registeredDetails.membershipExpiresOn.getYear.toString
                    )

                    Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet).bind(form), amlsBodies, mayBeGoBackUrl))

                  case Left(_) =>
                    Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet), amlsBodies, mayBeGoBackUrl))
                }

              case (None, _) => Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet), amlsBodies))
            }
          }
        }
      }
    }
  }

  def submitAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          amlsForm(amlsBodies.keys.toSet)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.formWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_details(form, amlsBodies))
              },
              validForm => {
                val amlsDetails = AMLSDetails(
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code")),
                  Right(RegisteredDetails(validForm.membershipNumber, validForm.expiry)))
                updateSession(existingSession, amlsDetails, agent)
                  .flatMap { _ =>
                    sessionStoreService.fetchContinueUrl.map {
                      case Some(_) => Redirect(routes.SubscriptionController.showCheckAnswers())
                      case None    => Redirect(routes.TaskListController.showTaskList())
                    }
                  }
              }
            )
        }
      }
    }
  }

  def showAmlsNotAppliedPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.amls.amls_not_applied())
    }
  }

  def showAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          for {
            cachedAmlsDetails <- existingSession.map(_.amlsDetails)
            cachedGoBackUrl   <- sessionStoreService.fetchGoBackUrl
          } yield {
            (cachedAmlsDetails, cachedGoBackUrl) match {
              case (Some(amlsDetails), mayBeGoBackUrl) =>
                amlsDetails.details match {
                  case Left(pendingDetails) =>
                    val form: Map[String, String] = Map(
                      "amlsCode"        -> "HMRC",
                      "appliedOn.day"   -> pendingDetails.appliedOn.getDayOfMonth.toString,
                      "appliedOn.month" -> pendingDetails.appliedOn.getMonthValue.toString,
                      "appliedOn.year"  -> pendingDetails.appliedOn.getYear.toString
                    )

                    Ok(
                      html.amls
                        .amls_pending_details(amlsPendingForm.bind(form), mayBeGoBackUrl))

                  case Right(_) =>
                    Ok(
                      html.amls
                        .amls_pending_details(amlsPendingForm, mayBeGoBackUrl))
                }

              case (None, _) => Ok(html.amls.amls_pending_details(amlsPendingForm))
            }
          }
        }
      }
    }
  }

  def submitAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        withManuallyAssuredAgent(existingSession) {
          amlsPendingForm
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.amlsPendingDetailsFormWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_pending_details(form))
              },
              validForm => {
                val amlsDetails = AMLSDetails(
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code")),
                  Left(PendingDetails(validForm.appliedOn)))

                updateSession(existingSession, amlsDetails, agent)
                  .flatMap { _ =>
                    sessionStoreService.fetchContinueUrl.map {
                      case Some(_) => Redirect(routes.SubscriptionController.showCheckAnswers())
                      case None    => Redirect(routes.TaskListController.showTaskList())
                    }
                  }
              }
            )
        }
      }
    }
  }

  private def withManuallyAssuredAgent(agentSession: AgentSession)(body: => Future[Result])(
    implicit hc: HeaderCarrier): Future[Result] =
    agentSession.utr match {
      case Some(utr) =>
        agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { response =>
          if (response) {
            sessionStoreService
              .cacheAgentSession(agentSession.copy(taskListFlags = agentSession.taskListFlags.copy(isMAA = true)))
              .flatMap(_ => toFuture(Redirect(routes.SubscriptionController.showCheckAnswers())))
          } else body
        }
      case None =>
        //redirect to task list ??? What happens if agent is on MAA List?
        Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())
      //Redirect(routes.UtrController.showUtrForm())
    }

  def updateSession(existingSession: AgentSession, amlsDetails: AMLSDetails, agent: Agent)(
    implicit hc: HeaderCarrier) = {
    val newSession = agent match {
      case hasNonEmptyEnrolments(_) =>
        existingSession
          .copy(
            amlsDetails = Some(amlsDetails),
            taskListFlags = existingSession.taskListFlags.copy(amlsTaskComplete = true))
      case _ =>
        existingSession
          .copy(
            amlsDetails = Some(amlsDetails),
            taskListFlags = existingSession.taskListFlags.copy(amlsTaskComplete = true, createTaskComplete = true))
    }
    sessionStoreService.cacheAgentSession(newSession)
  }
}
