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
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, PendingDate, RegDetails}
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

  def changeAmlsDetails: Action[AnyContent] = Action.async { implicit request =>
    sessionStoreService
      .cacheIsChangingAnswers(changing = true)
      .map(_ => Redirect(routes.AMLSController.showAmlsRegisteredPage()))
  }

  def showAmlsRegisteredPage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChange =>
          agent.getMandatorySubscriptionRecord.map { record =>
            record.amlsData match {
              case Some(amlsData) =>
                Ok(
                  check_amls(
                    checkAmlsForm.bind(Map("registeredAmls" -> RadioInputAnswer(amlsData.amlsRegistered))),
                    isChange = isChange.getOrElse(false)))
              case None => Ok(check_amls(checkAmlsForm, isChange.getOrElse(false)))
            }
          }
        }
      }
    }
  }

  def submitAmlsRegistered: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChange =>
          checkAmlsForm
            .bindFromRequest()
            .fold(
              formWithErrors => Ok(html.amls.check_amls(formWithErrors, isChange.getOrElse(false))),
              validForm => {
                val continue: Call = validForm match {
                  case Yes =>
                    routes.AMLSController.showAmlsDetailsForm()
                  case No => routes.AMLSController.showCheckAmlsAlreadyAppliedForm()
                }
                val cleanAmlsData = AmlsData(
                  amlsRegistered = RadioInputAnswer.toBoolean(validForm),
                  amlsAppliedFor = None,
                  supervisoryBody = None,
                  pendingDetails = None,
                  registeredDetails = None)

                updateAmlsJourneyRecord(
                  agent, { amlsData =>
                    {
                      if (amlsData.amlsRegistered == RadioInputAnswer.toBoolean(validForm)) Some(amlsData)
                      else Some(cleanAmlsData)
                    }
                  },
                  maybeCreateNewAmlsData = Some(cleanAmlsData)
                ).map(
                  _ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsRegisteredPage()))
                )
              }
            )
        }
      }
    }
  }

  def showCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        agent.getMandatoryAmlsData.map { data =>
          data.amlsAppliedFor match {
            case Some(appliedFor) =>
              Ok(amls_applied_for(appliedForAmlsForm.bind(Map("amlsAppliedFor" -> RadioInputAnswer(appliedFor)))))
            case None => Ok(amls_applied_for(appliedForAmlsForm))
          }
        }
      }
    }
  }

  def submitCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        appliedForAmlsForm.bindFromRequest.fold(
          formWithErrors => Ok(amls_applied_for(formWithErrors)),
          validForm => {
            val continue = validForm match {
              case Yes => routes.AMLSController.showAmlsApplicationDatePage()
              case No  => routes.AMLSController.showAmlsNotAppliedPage()
            }
            updateAmlsJourneyRecord(
              agent,
              amlsData => Some(amlsData.copy(amlsAppliedFor = Some(RadioInputAnswer.toBoolean(validForm))))).map(
              _ => Redirect(continueOrStop(continue, routes.AMLSController.showCheckAmlsAlreadyAppliedForm()))
            )
          }
        )
      }
    }
  }

  def showAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        for {
          record <- agent.getMandatoryAmlsData
        } yield
          (record.registeredDetails, record.supervisoryBody) match {
            case (Some(details), Some(supervisoryBody)) =>
              val form: Map[String, String] = Map(
                "amlsCode"         -> amlsBodies.find(_._2 == supervisoryBody).map(_._1).getOrElse(""),
                "membershipNumber" -> details.membershipNumber,
                "expiry.day"       -> details.membershipExpiresOn.getDayOfMonth.toString,
                "expiry.month"     -> details.membershipExpiresOn.getMonthValue.toString,
                "expiry.year"      -> details.membershipExpiresOn.getYear.toString
              )
              Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet).bind(form), amlsBodies))

            case _ => Ok(html.amls.amls_details(amlsForm(amlsBodies.keySet), amlsBodies))
          }
      }
    }
  }

  def submitAmlsDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
          amlsForm(amlsBodies.keys.toSet)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.formWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_details(form, amlsBodies))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))
                val continue = toTaskListOrCheckYourAnswers(isChanging)
                updateAmlsJourneyRecord(
                  agent,
                  amlsData =>
                    Some(
                      amlsData.copy(
                        supervisoryBody = Some(supervisoryBodyData),
                        registeredDetails = Some(RegDetails(validForm.membershipNumber, validForm.expiry))))
                ).map(
                  _ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsDetailsForm()))
                )
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
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        for {
          cachedAmlsData <- agent.getMandatoryAmlsData
        } yield {
          cachedAmlsData.pendingDetails match {
            case Some(pendingDetails) =>
              val form: Map[String, String] = Map(
                "amlsCode"        -> "HMRC",
                "appliedOn.day"   -> pendingDetails.appliedOn.getDayOfMonth.toString,
                "appliedOn.month" -> pendingDetails.appliedOn.getMonthValue.toString,
                "appliedOn.year"  -> pendingDetails.appliedOn.getYear.toString
              )
              Ok(
                html.amls
                  .amls_pending_details(amlsPendingForm.bind(form)))

            case None =>
              Ok(
                html.amls
                  .amls_pending_details(amlsPendingForm))
          }
        }
      }
    }
  }

  def submitAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
          amlsPendingForm
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val form = AMLSForms.amlsPendingDetailsFormWithRefinedErrors(formWithErrors)
                Ok(html.amls.amls_pending_details(form))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))

                val continue = toTaskListOrCheckYourAnswers(isChanging)
                updateAmlsJourneyRecord(
                  agent,
                  amlsData =>
                    Some(
                      amlsData.copy(
                        supervisoryBody = Some(supervisoryBodyData),
                        pendingDetails = Some(PendingDate(validForm.appliedOn))))
                ).map(
                  _ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsApplicationDatePage()))
                )
              }
            )
        }
      }
    }
  }

  private def continueOrStop(next: Call, previous: Call)(implicit request: Request[AnyContent]): Call = {
    val call = request.body.asFormUrlEncoded.get("continue").headOption match {
      case Some("continue") => next
      case Some("save")     => routes.TaskListController.savedProgress(Some(previous.url))
      case _ => {
        Logger.warn("unexpected value in submit")
        routes.TaskListController.showTaskList()
      }
    }
    call
  }

  private def toTaskListOrCheckYourAnswers(isChanging: Option[Boolean]) =
    if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
    else routes.TaskListController.showTaskList()

  private def withManuallyAssuredAgent(agent: Agent)(body: => Future[Result])(
    implicit hc: HeaderCarrier): Future[Result] = {
    val utr = agent.getMandatorySubscriptionRecord.businessDetails.utr
    agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { response =>
      if (response) toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
      else body
    }
  }

  private def updateAmlsJourneyRecord(
    agent: Agent,
    updateExistingAmlsData: AmlsData => Option[AmlsData],
    maybeCreateNewAmlsData: Option[AmlsData] = None)(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      record <- agent.getMandatorySubscriptionRecord
      updatedRecord <- {
        val newAmlsData: Option[AmlsData] = record.amlsData match {
          case Some(amlsData) => updateExistingAmlsData(amlsData)
          case None =>
            if (maybeCreateNewAmlsData.isDefined) maybeCreateNewAmlsData
            else throw new RuntimeException("No AMLS data found in record")
        }
        record.copy(amlsData = newAmlsData)
      }
      _ <- subscriptionJourneyService.saveJourneyRecord(updatedRecord)
    } yield ()
}
