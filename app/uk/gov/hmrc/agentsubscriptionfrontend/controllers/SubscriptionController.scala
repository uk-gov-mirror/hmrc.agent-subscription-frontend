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
import play.api.i18n.Lang
import play.api.mvc.{AnyContent, _}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.view._
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, AgentAssuranceConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{SubscriptionJourneyRecord, UserMapping}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{HttpError, MongoDBSessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.{toFuture, valueOps}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{address_form_with_errors, check_answers, sign_in_new_id, subscription_complete}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubscriptionController @Inject()(
                                        val authConnector: AuthConnector,
                                        subscriptionService: SubscriptionService,
                                        val sessionStoreService: MongoDBSessionStoreService,
                                        val metrics: Metrics,
                                        val config: Configuration,
                                        val env: Environment,
                                        addressLookUpConnector: AddressLookupFrontendConnector,
                                        agentAssuranceConnector: AgentAssuranceConnector,
                                        val redirectUrlActions: RedirectUrlActions,
                                        mcc: MessagesControllerComponents,
                                        val subscriptionJourneyService: SubscriptionJourneyService,
                                        checkAnswersTemplate: check_answers,
                                        addressFormWithErrorsTemplate: address_form_with_errors,
                                        subscriptionCompleteTemplate: subscription_complete,
                                        signInNewIdTemplate: sign_in_new_id)(
  implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc)
    with SessionBehaviour  with AuthActions {

  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  def showCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.withCleanCredsOrSignIn {
        val sjr = agent.getMandatorySubscriptionRecord
        agentAssuranceConnector.isManuallyAssuredAgent(sjr.businessDetails.utr).flatMap { isMAAgent =>
          sessionStoreService.cacheIsChangingAnswers(changing = false).flatMap { _ =>
            CYACheckResult.check(sjr) match {
              case PassWithMaybeAmls(taxpayerName, address, maybeAmls, contactEmail, maybeTradingName, tradingAddress) => {
                if (maybeAmls.isDefined || isMAAgent) {
                  sessionStoreService
                    .cacheGoBackUrl(routes.SubscriptionController.showCheckAnswers().url)
                    .map { _ =>
                      Ok(
                        checkAnswersTemplate(CheckYourAnswers(
                          registrationName = taxpayerName,
                          address = address,
                          amlsData = maybeAmls,
                          isManuallyAssured = isMAAgent,
                          userMappings = sjr.userMappings,
                          continueId = sjr.continueId,
                          contactEmailAddress = contactEmail,
                          contactTradingName = maybeTradingName,
                          contactTradingAddress = tradingAddress,
                          appConfig)
                        ))
                    }
                } else Redirect(routes.AMLSController.showAmlsRegisteredPage())
              }

              case FailedRegistration => Redirect(routes.BusinessTypeController.showBusinessTypeForm())

              case FailedContactEmail => Redirect(routes.ContactDetailsController.showContactEmailCheck())

              case FailedContactTradingName => Redirect(routes.ContactDetailsController.showTradingNameCheck())

              case FailedContactTradingAddress => Redirect(routes.ContactDetailsController.showCheckMainTradingAddress())

            }
          }
        }
      }
    }
  }

  //helper function to copy record details to session before the record is deleted allowing them to be displayed on subscription complete page
  private def updateSessionAndReturnAgencyBeforeSubscribing(registration: Registration)
                                            (emailData: ContactEmailData,
                                             nameData: ContactTradingNameData,
                                             addressData: ContactTradingAddressData, userMappings: List[UserMapping])(implicit hc: HeaderCarrier) = {
    val agencyName = nameData.contactTradingName.orElse(registration.taxpayerName)
    val agencyEmail = emailData.contactEmail
    val agencyAddress: BusinessAddress = addressData.contactTradingAddress.getOrElse(
      throw new Exception("contact trading address should be defined"))
    val clientCount = userMappings.map(_.count).sum

    sessionStoreService.cacheAgentSession(AgentSession(registration = Some(
      Registration(taxpayerName = agencyName,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = false,
        agencyAddress,
        agencyEmail)), clientCount = Some(clientCount))).map(_ => Agency(
      agencyName.getOrElse(registration.taxpayerName.getOrElse(throw new Exception("taxpayer name should be defined"))),
      DesAddress.fromBusinessAddress(agencyAddress),
      agencyEmail.getOrElse(throw new Exception("contact email address should be defined"))))
  }

  def submitCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.withCleanCredsOrSignIn {
        val sjr = agent.getMandatorySubscriptionRecord
        (sjr.businessDetails.utr,
          sjr.businessDetails.postcode,
          sjr.businessDetails.registration,
          sjr.amlsData,
          sjr.contactEmailData,
          sjr.contactTradingNameData,
          sjr.contactTradingAddressData,
        sjr.userMappings) match {
          case (utr, postcode, Some(registration), amlsData, Some(email), Some(name), Some(address), userMappings) => {
            for {
              agencyDetails <- updateSessionAndReturnAgencyBeforeSubscribing(registration)(email, name, address, userMappings)
              langForEmail = extractLangPreferenceFromCookie
              _ <- subscriptionService.subscribe(utr, postcode, agencyDetails, langForEmail, amlsData)
              result <- redirectSubscriptionResponse
            } yield result
          } recoverWith {
            case HttpError(_, CONFLICT) =>
              mark("Count-Subscription-AlreadySubscribed-APIResponse")
              Future successful Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())
            case HttpError(_, INTERNAL_SERVER_ERROR) =>
              mark("Count-Subscription-Failed-Agent_Terminated")
              Future successful Redirect(routes.StartController.showCannotCreateAccount())
            case HttpError(_, status) =>
              mark("Count-Subscription-Failed")
              Future failed new HttpException(s"Subscription failed: HTTP status $status from agent-subscription service ", status)
          }
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
      implicit val language: Lang = mcc.messagesApi.preferred(request).lang
      addressLookUpConnector
        .initJourney(routes.SubscriptionController.returnFromAddressLookup())
        .map(Redirect(_))
    }
  }

  private def extractLangPreferenceFromCookie(implicit request: Request[_]): Option[Lang] =
    request.cookies
      .get("PLAY_LANG").map(x => Lang(x.value))


  private def redirectSubscriptionResponse: Future[Result] = {
    mark("Count-Subscription-Complete")
    Redirect(routes.SubscriptionController.showSubscriptionComplete())
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
                formWithErrors => Future successful Ok(addressFormWithErrorsTemplate(formWithErrors)),
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
            formWithErrors => Future successful Ok(addressFormWithErrorsTemplate(formWithErrors)),
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
    withSubscribedAgent { (arn: Arn, sjrOpt: Option[SubscriptionJourneyRecord]) =>
      for{
        maybeContinueUrl <- getMaybeContinueUrl
        (name, email, clientCount) <- agencyNameAndEmailClientCount(sjrOpt)

      } yield Ok(
        subscriptionCompleteTemplate(
          arn.value, name, email, clientCount))
    }
  }

  private def agencyNameAndEmailClientCount(maybeSjr: Option[SubscriptionJourneyRecord])(implicit hc: HeaderCarrier): Future[(String, String, Int)] = {
    maybeSjr match {
      case Some(sjr) => {

        val agencyName: String = sjr.contactTradingNameData.flatMap(_.contactTradingName).getOrElse(
          sjr.businessDetails.registration.flatMap(_.taxpayerName).getOrElse(throw new Exception("taxpayer name should be defined"))
        )

        val agencyEmail: String = sjr.contactEmailData.flatMap(_.contactEmail).getOrElse(
          sjr.businessDetails.registration.flatMap(_.emailAddress).getOrElse(throw new Exception("business email address should be defined"))
        )

        val clientCount = sjr.userMappings.map(_.count).sum

        (agencyName, agencyEmail, clientCount)
      }.toFuture
      case None => {
        sessionStoreService.fetchAgentSession.map {
          case Some(agentSession) => {
            val reg = agentSession.registration.getOrElse(throw new Exception("agent session should have a registration "))
            val agencyName = reg.taxpayerName.getOrElse(throw new Exception("taxpayer name should be defined"))
            val agencyEmail = reg.emailAddress.getOrElse(throw new Exception("agency email should be defined"))
            val clientCount = agentSession.clientCount.getOrElse(0)
            (agencyName, agencyEmail, clientCount)
          }
          case None => throw new RuntimeException("no agent session found")
        }
      }
    }
  }

  private def getMaybeContinueUrl(implicit hc: HeaderCarrier): Future[Option[String]] = {

    def recoverSessionStoreWithNone[T]: PartialFunction[Throwable, Option[T]] = {
      case NonFatal(ex) =>
        Logger(getClass).warn("Session store service failure", ex)
        None
    }

    for {
      continueUrl <- sessionStoreService.fetchContinueUrl.recover(recoverSessionStoreWithNone)
      redirectUrlOpt <- redirectUrlActions.getUrl(continueUrl)
    } yield redirectUrlOpt
  }


  def showSignInWithNewID: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(signInNewIdTemplate())
    }
  }
}



