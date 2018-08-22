/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, MappingConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.models.StoreEligibility.{IsEligible, IsNotEligible, MappingUnavailable}
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionReturnedHttpError, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpException}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SubscriptionDetails(utr: Utr, knownFactsPostcode: String, name: String, email: String, address: DesAddress)

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(initDetails: InitialDetails, address: DesAddress): SubscriptionDetails =
    SubscriptionDetails(
      initDetails.utr,
      initDetails.knownFactsPostcode,
      initDetails.name,
      initDetails.email.getOrElse(throw new Exception("email should not be empty")),
      address)
}

@Singleton
class SubscriptionController @Inject()(
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  subscriptionService: SubscriptionService,
  sessionStoreService: SessionStoreService,
  addressLookUpConnector: AddressLookupFrontendConnector,
  mappingConnector: MappingConnector,
  val continueUrlActions: ContinueUrlActions,
  val metrics: Metrics,
  override val appConfig: AppConfig)(implicit val aConfig: AppConfig)
    extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  private val linkAccountForm: Form[LinkAccount] =
    Form[LinkAccount](
      mapping("autoMapping" -> optional(text).verifying(FieldMappings.radioInputSelected))(ans =>
        LinkAccount(RadioInputAnswer.apply(ans.getOrElse(""))))(lc => Some(RadioInputAnswer.unapply(lc.autoMapping)))
        .verifying(
          "error.link-account-value.invalid",
          submittedLinkAccount => Seq(Yes, No).contains(submittedLinkAccount.autoMapping)))

  private val businessNameForm = Form[BusinessName](
    mapping(
      "name" -> agencyName
    )(BusinessName.apply)(BusinessName.unapply)
  )

  private val businessEmailForm = Form[BusinessEmail](
    mapping(
      "email" -> emailAddress
    )(BusinessEmail.apply)(BusinessEmail.unapply)
  )

  val showCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent {
      case hasNonEmptyEnrolments(_) =>
        Future.successful(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
      case _ =>
        mark("Count-Subscription-CleanCreds-Success")
        sessionStoreService.fetchInitialDetails.map(_.map { details =>
          if (details.email.nonEmpty)
            Ok(
              html.check_answers(
                registrationName = details.name,
                address = details.businessAddress,
                emailAddress = details.email
              ))
          else
            Redirect(routes.SubscriptionController.showBusinessEmailForm())
        }.getOrElse {
          sessionMissingRedirect()
        })
    }
  }

  val submitCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent {
      case hasNonEmptyEnrolments(_) =>
        Future.successful(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
      case _ =>
        sessionStoreService.fetchInitialDetails.flatMap(_.map { details =>
          val desAddress = DesAddress(
            details.businessAddress.addressLine1,
            details.businessAddress.addressLine2,
            details.businessAddress.addressLine3,
            details.businessAddress.addressLine4,
            details.businessAddress.postalCode.getOrElse(throw new Exception("Postcode should not be empty")),
            details.businessAddress.countryCode
          )
          subscriptionService.subscribe(details, desAddress).flatMap(redirectSubscriptionResponse)
        }.getOrElse {
          Future.successful(sessionMissingRedirect())
        })
    }
  }

  val showBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.map(_.map { details =>
        Ok(html.business_name(businessNameForm.bind(Map("name" -> details.name))))
      }.getOrElse {
        sessionMissingRedirect()
      })
    }
  }

  val submitBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.flatMap(_.map { details =>
        businessNameForm
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(Ok(html.business_name(formWithErrors))),
            validForm =>
              sessionStoreService
                .cacheInitialDetails(details.copy(name = validForm.name))
                .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
          )
      }.getOrElse {
        Future.successful(sessionMissingRedirect())
      })
    }
  }

  val showBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.map(_.map { details =>
        val form =
          if (details.email.nonEmpty) businessEmailForm.bind(Map("email" -> details.email.get)) else businessEmailForm

        Ok(html.business_email(form, details.email))
      }.getOrElse {
        sessionMissingRedirect()
      })
    }
  }

  val submitBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.flatMap(_.map { details =>
        businessEmailForm
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(Ok(html.business_email(formWithErrors, details.email))),
            validForm =>
              sessionStoreService
                .cacheInitialDetails(details.copy(email = Some(validForm.email)))
                .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
          )
      }.getOrElse {
        Future.successful(sessionMissingRedirect())
      })
    }
  }

  val showBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.flatMap(_.map { _ =>
        mark("Count-Subscription-AddressLookup-Start")
        addressLookUpConnector
          .initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName)
          .map(Redirect(_))
      }.getOrElse {
        Future.successful(sessionMissingRedirect())
      })
    }
  }

  private def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)])(
    implicit request: Request[AnyContent]): Future[Result] =
    either match {
      case Right((arn, _)) =>
        mark("Count-Subscription-Complete")
        redirectUponSuccessfulSubscription(arn)

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Future.successful(Redirect(routes.CheckAgencyController.showAlreadySubscribed()))

      case Left(SubscriptionReturnedHttpError(status)) =>
        mark("Count-Subscription-Failed")
        throw new HttpException("Subscription failed", status)
    }

  private[controllers] def redirectUponSuccessfulSubscription(arn: Arn)(implicit request: Request[AnyContent]) =
    for (redirectLocation <- if (appConfig.autoMapAgentEnrolments) {
                              sessionStoreService.fetchMappingEligible.map {
                                StoreEligibility.apply(_) match {
                                  case IsEligible    => routes.SubscriptionController.showLinkAccount()
                                  case IsNotEligible => routes.SubscriptionController.showSubscriptionComplete()
                                  case MappingUnavailable => {
                                    Logger.warn("chainedSessionDetails did not cache wasEligibleForMapping")
                                    routes.SubscriptionController.showSubscriptionComplete()
                                  }
                                }
                              }
                            } else Future successful routes.SubscriptionController.showSubscriptionComplete())
      yield Redirect(redirectLocation).withSession(request.session + ("arn" -> arn.value))

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchInitialDetails.flatMap { maybeDetails =>
        maybeDetails
          .map { details =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              desAddressForm
                .bindAddressLookupFrontendAddress(details.utr, address)
                .fold(
                  formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
                  validDesAddress => {
                    mark("Count-Subscription-AddressLookup-Success")
                    sessionStoreService
                      .cacheInitialDetails(details.copy(
                        businessAddress = BusinessAddress(validDesAddress)
                      ))
                      .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
                  }
                )
            }
          }
          .getOrElse(Future.successful(sessionMissingRedirect()))
      }
    }
  }

  def submitModifiedAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      desAddressForm.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
          validDesAddress =>
            sessionStoreService.fetchInitialDetails.flatMap { maybeInitialDetails =>
              maybeInitialDetails
                .map { initialDetails =>
                  sessionStoreService
                    .cacheInitialDetails(initialDetails.copy(
                      businessAddress = BusinessAddress(validDesAddress)
                    ))
                    .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
                }
                .getOrElse(Future.successful(sessionMissingRedirect()))
          }
        )
    }
  }

  val showLinkAccount: Action[AnyContent] = Action.async { implicit request =>
    appConfig.autoMapAgentEnrolments match {
      case true =>
        withAuthenticatedAgent {
          Future.successful {
            request.session.get("arn").fold(sessionMissingRedirect()) { _ =>
              Ok(html.link_account(linkAccountForm))
            }
          }
        }
      case false => Future.successful(InternalServerError)
    }
  }

  val submitLinkAccount: Action[AnyContent] = Action.async { implicit request =>
    appConfig.autoMapAgentEnrolments match {
      case true =>
        withAuthenticatedAgent {
          request.session.get("arn").fold(Future.successful(sessionMissingRedirect())) { arn =>
            linkAccountForm
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  if (formWithErrors.errors.exists(_.message == "error.link-account-value.invalid")) {
                    throw new BadRequestException("Form submitted with strange input value")
                  } else {
                    Future.successful(Ok(html.link_account(formWithErrors)))
                  }
                },
                validatedLinkAccount => {
                  validatedLinkAccount.autoMapping match {
                    case Yes => linkAccountResponse(mappingConnector.updatePreSubscriptionWithArn)
                    case No  => linkAccountResponse(mappingConnector.deletePreSubscription)
                  }
                }
              )
          }
        }
      case false => Future.successful(InternalServerError)
    }
  }

  val showSubscriptionComplete: Action[AnyContent] = Action.async { implicit request =>
    def recoverSessionStoreWithNone[T]: PartialFunction[Throwable, Option[T]] = {
      case NonFatal(ex) =>
        Logger(getClass).warn("Session store service failure", ex)
        None
    }

    withAuthenticatedAgent {
      request.session.get("arn") match {
        case Some(arn) =>
          for {
            continueUrlOpt           <- sessionStoreService.fetchContinueUrl.recover(recoverSessionStoreWithNone)
            wasEligibleForMappingOpt <- sessionStoreService.fetchMappingEligible.recover(recoverSessionStoreWithNone)
            _                        <- sessionStoreService.remove()
          } yield {
            val continueUrl = continueUrlOpt.map(_.url).getOrElse(appConfig.agentServicesAccountUrl)
            val isUrlToASAccount = continueUrlOpt.isEmpty
            val wasEligibleForMapping = wasEligibleForMappingOpt.contains(true)
            val prettifiedArn = prettify(Arn(arn))
            Ok(html.subscription_complete(continueUrl, isUrlToASAccount, wasEligibleForMapping, prettifiedArn))
              .removingFromSession("arn")
          }
        case _ =>
          Future.successful(sessionMissingRedirect())
      }
    }
  }

  private def linkAccountResponse[A](
    body: Utr => Future[Unit])(implicit hc: HeaderCarrier, request: Request[A]): Future[Result] =
    sessionStoreService.fetchKnownFactsResult.flatMap {
      _.map(_.utr).fold(Future.successful(sessionMissingRedirect())) { utr =>
        body(utr).map(_ => Redirect(routes.SubscriptionController.showSubscriptionComplete()))
      }
    }
}
