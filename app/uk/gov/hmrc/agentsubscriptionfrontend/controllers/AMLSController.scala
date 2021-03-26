/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.mvc.{AnyContent, _}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.service.{MongoDBSessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.amls._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AMLSController @Inject()(
  val config: Configuration,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val redirectUrlActions: RedirectUrlActions,
  val subscriptionJourneyService: SubscriptionJourneyService,
  val sessionStoreService: MongoDBSessionStoreService,
  agentAssuranceConnector: AgentAssuranceConnector,
  mcc: MessagesControllerComponents,
  checkAmlsTemplate: check_amls,
  amlsAppliedForTemplate: amls_applied_for,
  amlsNotAppliedTemplate: amls_not_applied,
  amlsDetailsTemplate: amls_details,
  amlsPendingDetailsTemplate: amls_pending_details)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

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
                  checkAmlsTemplate(
                    checkAmlsForm.bind(Map("registeredAmls" -> RadioInputAnswer(amlsData.amlsRegistered))),
                    isChange = isChange.getOrElse(false)))
              case None => Ok(checkAmlsTemplate(checkAmlsForm, isChange.getOrElse(false)))
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
              formWithErrors => Ok(checkAmlsTemplate(formWithErrors, isChange.getOrElse(false))),
              validForm => {
                val continue: Call = validForm match {
                  case Yes =>
                    routes.AMLSController.showAmlsDetailsForm()
                  case No => routes.AMLSController.showCheckAmlsAlreadyAppliedForm()
                }
                val cleanAmlsData = AmlsData(amlsRegistered = RadioInputAnswer.toBoolean(validForm), amlsAppliedFor = None, amlsDetails = None)

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
              Ok(amlsAppliedForTemplate(appliedForAmlsForm.bind(Map("amlsAppliedFor" -> RadioInputAnswer(appliedFor)))))
            case None => Ok(amlsAppliedForTemplate(appliedForAmlsForm))
          }
        }
      }
    }
  }

  def submitCheckAmlsAlreadyAppliedForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        appliedForAmlsForm.bindFromRequest.fold(
          formWithErrors => Ok(amlsAppliedForTemplate(formWithErrors)),
          validForm => {
            val continue = validForm match {
              case Yes => routes.AMLSController.showAmlsApplicationDatePage()
              case No  => routes.AMLSController.showAmlsNotAppliedPage()
            }
            updateAmlsJourneyRecord(agent, amlsData => Some(amlsData.copy(amlsAppliedFor = Some(RadioInputAnswer.toBoolean(validForm))))).map(
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
          record.amlsDetails match {
            case Some(amlsDetails) =>
              amlsDetails.details match {
                case Left(PendingDetails(_)) => Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet), amlsBodies))
                case Right(RegisteredDetails(membershipNumber, membershipExpiresOn)) =>
                  val form: Map[String, String] = Map(
                    "amlsCode"         -> amlsBodies.find(_._2 == amlsDetails.supervisoryBody).map(_._1).getOrElse(""),
                    "membershipNumber" -> membershipNumber,
                    "expiry.day"       -> membershipExpiresOn.getDayOfMonth.toString,
                    "expiry.month"     -> membershipExpiresOn.getMonthValue.toString,
                    "expiry.year"      -> membershipExpiresOn.getYear.toString
                  )
                  Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet).bind(form), amlsBodies))
              }
            case _ => Ok(amlsDetailsTemplate(amlsForm(amlsBodies.keySet), amlsBodies))
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
                Ok(amlsDetailsTemplate(form, amlsBodies))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))
                val continue = toTaskListOrCheckYourAnswers(isChanging)
                updateAmlsJourneyRecord(
                  agent,
                  amlsData =>
                    Some(amlsData.copy(
                      amlsDetails = Some(AmlsDetails(supervisoryBodyData, Right(RegisteredDetails(validForm.membershipNumber, validForm.expiry))))))
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
      Ok(amlsNotAppliedTemplate())
    }
  }

  def showAmlsApplicationDatePage: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withManuallyAssuredAgent(agent) {
        for {
          amlsData <- agent.getMandatoryAmlsData
        } yield {
          amlsData.amlsDetails match {
            case Some(amlsDetails) =>
              amlsDetails.details match {
                case Left(PendingDetails(appliedOn)) =>
                  val form: Map[String, String] = Map(
                    "amlsCode"        -> "HMRC",
                    "appliedOn.day"   -> appliedOn.getDayOfMonth.toString,
                    "appliedOn.month" -> appliedOn.getMonthValue.toString,
                    "appliedOn.year"  -> appliedOn.getYear.toString
                  )
                  Ok(amlsPendingDetailsTemplate(amlsPendingForm.bind(form)))
                case Right(RegisteredDetails(_, _)) => Ok(amlsPendingDetailsTemplate(amlsPendingForm))
              }
            case _ => Ok(amlsPendingDetailsTemplate(amlsPendingForm))
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
                Ok(amlsPendingDetailsTemplate(form))
              },
              validForm => {
                val supervisoryBodyData =
                  amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code"))

                val continue = toTaskListOrCheckYourAnswers(isChanging)
                updateAmlsJourneyRecord(
                  agent,
                  amlsData => Some(amlsData.copy(amlsDetails = Some(AmlsDetails(supervisoryBodyData, Left(PendingDetails(validForm.appliedOn))))))
                ).map(
                  _ => Redirect(continueOrStop(continue, routes.AMLSController.showAmlsApplicationDatePage()))
                )
              }
            )
        }
      }
    }
  }

  private def toTaskListOrCheckYourAnswers(isChanging: Option[Boolean]) =
    if (isChanging.getOrElse(false)) routes.SubscriptionController.showCheckAnswers()
    else routes.TaskListController.showTaskList()

  private def withManuallyAssuredAgent(agent: Agent)(body: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    val utr = agent.getMandatorySubscriptionRecord.businessDetails.utr
    agentAssuranceConnector.isManuallyAssuredAgent(utr).flatMap { response =>
      if (response) toFuture(Redirect(routes.TaskListController.showTaskList()))
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
