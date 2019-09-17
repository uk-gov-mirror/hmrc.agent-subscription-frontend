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
import uk.gov.hmrc.agentsubscriptionfrontend.config.view.CheckYourAnswers
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, AgentAssuranceConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionReturnedHttpError, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.sign_in_new_id
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubscriptionController @Inject()(
                                        override val authConnector: AuthConnector,
                                        subscriptionService: SubscriptionService,
                                        val sessionStoreService: SessionStoreService,
                                        addressLookUpConnector: AddressLookupFrontendConnector,
                                        agentAssuranceConnector: AgentAssuranceConnector,
                                        redirectUrlActions: RedirectUrlActions,
                                        override val subscriptionJourneyService: SubscriptionJourneyService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, redirectUrlActions, appConfig, subscriptionJourneyService)
    with SessionBehaviour {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  def showCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.withCleanCredsOrSignIn {
        val sjr = agent.getMandatorySubscriptionRecord
        agentAssuranceConnector.isManuallyAssuredAgent(sjr.businessDetails.utr).flatMap { isMAAgent =>
          sessionStoreService.cacheIsChangingAnswers(changing = false).flatMap { _ =>
            (sjr.businessDetails.registration, sjr.amlsData) match {
              case (Some(registration), Some(amlsData)) =>
                sessionStoreService
                  .cacheGoBackUrl(routes.SubscriptionController.showCheckAnswers().url)
                  .map { _ =>
                    Ok(
                      html.check_answers(CheckYourAnswers(
                        registrationName = registration.taxpayerName.getOrElse(""),
                        address = registration.address,
                        emailAddress = registration.emailAddress,
                        amlsData = Some(amlsData),
                        isManuallyAssured = isMAAgent,
                        userMappings = sjr.userMappings,
                        continueId = sjr.continueId,
                        appConfig)
                      ))
                  }

              case (None, _) => Redirect(routes.BusinessTypeController.showBusinessTypeForm())

              case (Some(registration), None) if isMAAgent =>
                Ok(
                  html.check_answers(CheckYourAnswers(
                    registrationName = registration.taxpayerName.getOrElse(""),
                    address = registration.address,
                    emailAddress = registration.emailAddress,
                    amlsData = None,
                    isManuallyAssured = isMAAgent,
                    userMappings = sjr.userMappings,
                    continueId = sjr.continueId,
                    appConfig)
                  ))

              case (_, None) => Redirect(routes.AMLSController.showAmlsRegisteredPage())
            }
          }
        }
      }
    }
  }

  //helper function to copy record details to session before the record is deleted allowing them to be displayed on subscription complete page
  private def updateSessionBeforeSubscribing(registration: Registration)(implicit hc: HeaderCarrier) = {
    val agencyName = registration.taxpayerName
    val agencyEmail = registration.emailAddress
    val agencyAddress = registration.address

    sessionStoreService.cacheAgentSession(AgentSession(registration = Some(
      Registration(taxpayerName = agencyName,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = false,
        agencyAddress,
        agencyEmail))))
  }

  def submitCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.withCleanCredsOrSignIn {
        val sjr = agent.getMandatorySubscriptionRecord
        (sjr.businessDetails.utr, sjr.businessDetails.postcode, sjr.businessDetails.registration, sjr.amlsData) match {
          case (utr, postcode, Some(registration), amlsData) =>
                    for {
                    _ <- updateSessionBeforeSubscribing(registration)
                    subscriptionResponse <- subscriptionService
                      .subscribe(utr, postcode, registration, amlsData)
                    result <- redirectSubscriptionResponse(subscriptionResponse, agent)
                    } yield result

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
          gotoComplete <- Redirect(routes.SubscriptionController.showSubscriptionComplete())
        } yield gotoComplete

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())

      case Left(SubscriptionReturnedHttpError(status)) =>
        mark("Count-Subscription-Failed")
        throw new HttpException(s"Subscription failed: HTTP status $status from agent-subscription service ", status)
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
                  } yield goto
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
                  throw new IllegalStateException("expecting registration in the session, but not found")
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

    def handleRegistrationAndGoToComplete(registration: Option[Registration], result: (String, String, String) => Result): Future[Result] = {
      registration match {
        case Some(registration) =>
          val agencyName = registration.taxpayerName.getOrElse(
            throw new RuntimeException("agency name is missing from registration"))
          val agencyEmail = registration.emailAddress.getOrElse(
            throw new RuntimeException("agency email is missing from registration"))


          for {
          continueUrl <- sessionStoreService.fetchContinueUrl.recover(recoverSessionStoreWithNone)
          redirectUrlOpt <- redirectUrlActions.getUrl(continueUrl)
          } yield redirectUrlOpt match {
              case Some(redirectUrl) => result(agencyName, agencyEmail, redirectUrl)
              case None =>
                val asaUrl = appConfig.agentServicesAccountUrl
                result(agencyName, agencyEmail, asaUrl)
            }

        case None =>
          Logger.warn("no registration details found for agent")
          Redirect(routes.BusinessIdentificationController.showNoMatchFound())
      }
    }

    withSubscribedAgent { (arn, sjrOpt) =>
      sjrOpt match {
        case Some(sjr) => handleRegistrationAndGoToComplete(sjr.businessDetails.registration, (agencyName, agencyEmail, redirectUrl) =>
            Ok(html.subscription_complete(redirectUrl, arn.value, agencyName, agencyEmail)))
        case None => sessionStoreService.fetchAgentSession.flatMap {
          case Some(agentSession) => handleRegistrationAndGoToComplete(agentSession.registration, (agencyName, agencyEmail, redirectUrl) =>
            Ok(html.subscription_complete(redirectUrl, arn.value, agencyName, agencyEmail)))

          case None => throw new RuntimeException("no record found for agent")
        }
      }
    }
  }

  def showSignInWithNewID: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(sign_in_new_id())
    }
  }
}



