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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, AgentAssuranceConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionReturnedHttpError, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.None
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubscriptionController @Inject()(
  override val authConnector: AuthConnector,
  subscriptionService: SubscriptionService,
  val sessionStoreService: SessionStoreService,
  addressLookUpConnector: AddressLookupFrontendConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  continueUrlActions: ContinueUrlActions,
  override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  def showCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withCleanCreds(agent) {
        val sjr = agent.getMandatorySubscriptionRecord
        agentAssuranceConnector.isManuallyAssuredAgent(sjr.businessDetails.utr).flatMap { isMAAgent =>
          sessionStoreService.cacheIsChangingAnswers(false).flatMap { _ =>
    (sjr.businessDetails.registration, sjr.amlsData) match {
      case (Some(registration), Some(amlsData)) =>
        sessionStoreService
          .cacheGoBackUrl(routes.SubscriptionController.showCheckAnswers().url)
          .map { _ =>
            Ok(
              html.check_answers(
                registrationName = registration.taxpayerName.getOrElse(""),
                address = registration.address,
                emailAddress = registration.emailAddress,
                amlsData = Some(amlsData)
              ))
          }

      case (None, _) => Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())

      case (Some(registration), None) if isMAAgent =>
        Ok(
          html.check_answers(
            registrationName = registration.taxpayerName.getOrElse(""),
            address = registration.address,
            emailAddress = registration.emailAddress,
            amlsData = None
          ))

      case (_, None) => Redirect(routes.AMLSController.showAmlsRegisteredPage())
    }
  }
        }
      }
    }
  }

  def submitCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withCleanCreds(agent) {
        val sjr = agent.getMandatorySubscriptionRecord
        (sjr.businessDetails.utr, sjr.businessDetails.postcode, sjr.businessDetails.registration, sjr.amlsData) match {
          case (utr, postcode, Some(registration), amlsData) =>
            subscriptionService
              .subscribe(utr, postcode, registration, amlsData)
              .flatMap(redirectSubscriptionResponse(_, agent))

          case _ =>
            Logger(getClass).warn(s"Missing data in session, redirecting back to /business-type")
            Redirect(routes.BusinessTypeController.showBusinessTypeForm())
        }
      }
    }
  }

  def showBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
        mark("Count-Subscription-AddressLookup-Start")
        addressLookUpConnector
          .initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName)
          .map(Redirect(_))
    }
  }

  private def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)], agent: Agent)(
    implicit request: Request[AnyContent]): Future[Result] =
    either match {
      case Right((_, _)) =>
        mark("Count-Subscription-Complete")
        for {
          sjr          <- agent.getMandatorySubscriptionRecord
          newRecord    <- sjr.copy(subscriptionCreated = true)
          _            <- subscriptionJourneyService.saveJourneyRecord(newRecord)
          gotoComplete <- Redirect(routes.SubscriptionController.showSubscriptionComplete())
        } yield gotoComplete

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())

      case Left(SubscriptionReturnedHttpError(status)) =>
        mark("Count-Subscription-Failed")
        throw new HttpException("Subscription failed", status)
    }

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
    val sjr = agent.getMandatorySubscriptionRecord
        sjr.businessDetails.utr match {
          case utr =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              desAddressForm
                .bindAddressLookupFrontendAddress(utr, address)
                .fold(
                  formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
                  validDesAddress => {
                    mark("Count-Subscription-AddressLookup-Success")
                    val updatedSjr = sjr.copy(businessDetails = sjr.businessDetails.copy(
                      registration = Some(sjr.businessDetails.registration
                        .getOrElse(throw new RuntimeException("missing registration data"))
                        .copy(address = BusinessAddress(validDesAddress)))))
                    for {
                    _ <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
                    goto <- Redirect(routes.SubscriptionController.showCheckAnswers())
                    }yield goto
                  }
                )
            }
        }
    }
  }

  def submitModifiedAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        desAddressForm.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
            validDesAddress => {
              val updatedReg = existingSession.registration match {
                case Some(reg) => reg.copy(address = BusinessAddress(validDesAddress))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") //TODO
              }
              sessionStoreService
                .cacheAgentSession(existingSession.copy(registration = Some(updatedReg)))
                .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
            }
          )
      }
    }
  }

  def showSubscriptionComplete: Action[AnyContent] = Action.async { implicit request =>

    def recoverSessionStoreWithNone[T]: PartialFunction[Throwable, Option[T]] = {
      case NonFatal(ex) =>
        Logger(getClass).warn("Session store service failure", ex)
        None
    }

    withSubscribedAgent { (arn, sjr) =>
        sjr.businessDetails.registration match {
          case Some(registration) =>
            val agencyName = registration.taxpayerName.getOrElse(
              throw new RuntimeException("agency name is missing from registration"))
            val agencyEmail = registration.emailAddress.getOrElse(
              throw new RuntimeException("agency email is missing from registration"))

            sessionStoreService.fetchContinueUrl.recover(recoverSessionStoreWithNone)
              .flatMap {
                  case Some(continueUrl) =>
                    for {
                      _ <- subscriptionJourneyService.deleteJourneyRecord(sjr.authProviderId)
                      result <- Ok(html.subscription_complete(continueUrl.url, arn.value, agencyName, agencyEmail))
                    } yield result

                  case None =>
                    Ok(html.subscription_complete(routes.TaskListController.showTaskList().url, arn.value, agencyName, agencyEmail))
                }

          case None =>
            Logger.warn("no registration details found in agent session")
            Redirect(routes.BusinessIdentificationController.showNoMatchFound())
        }
    }
  }

  // Temporary endpoint, just used while mapping is the final task in list
  def beginMapping: Action[AnyContent] = Action.async { implicit request =>
    withSubscribedAgent { (_, sjr) =>
      for {
        _ <- subscriptionJourneyService.deleteJourneyRecord(sjr.authProviderId)
      } yield Redirect(appConfig.agentMappingFrontendStartUrl)
    }
  }

}



