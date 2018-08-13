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
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.LinkAccountAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionReturnedHttpError, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpException}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SubscriptionDetails(
  utr: Utr,
  knownFactsPostcode: String,
  name: String,
  email: String,
  telephone: String,
  address: DesAddress)

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(initDetails: InitialDetails, address: DesAddress): SubscriptionDetails =
    SubscriptionDetails(
      initDetails.utr,
      initDetails.knownFactsPostcode,
      initDetails.name,
      initDetails.email,
      initDetails.telephone,
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
        LinkAccount(LinkAccountAnswer.apply(ans.getOrElse(""))))(lc => Some(LinkAccountAnswer.unapply(lc.autoMapping)))
        .verifying(
          "error.link-account-value.invalid",
          submittedLinkAccount => Seq(Yes, No).contains(submittedLinkAccount.autoMapping)))

  private val initialDetailsForm = Form[InitialDetails](
    mapping(
      "utr"                -> utr,
      "knownFactsPostcode" -> postcode,
      "name"               -> agencyName,
      "email"              -> emailAddress,
      "telephone"          -> telephone)(
      (utrStr, postcode, name, email, telephone) =>
        FieldMappings
          .normalizeUtr(utrStr)
          .map(utr => InitialDetails(utr, postcode, name, email, telephone))
          .getOrElse(throw new Exception("Invalid utr found after validation")))(id =>
      Some((id.utr.value, id.knownFactsPostcode, id.name, id.email, id.telephone))))

  val showInitialDetails: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent {
      case hasNonEmptyEnrolments(_) => Future(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
      case _ =>
        mark("Count-Subscription-CleanCreds-Success")
        sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
          Ok(html.subscription_details(
            knownFactsResult.taxpayerName,
            initialDetailsForm.fill(InitialDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null))))
        }.getOrElse {
          sessionMissingRedirect()
        })
    }
  }

  val submitInitialDetails: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      initialDetailsForm
        .bindFromRequest()
        .fold(
          formWithErrors => redisplayInitialDetails(formWithErrors),
          form => {
            mark("Count-Subscription-AddressLookup-Start")
            addressLookUpConnector
              .initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName)
              .map { x =>
                sessionStoreService.cacheInitialDetails(
                  InitialDetails(form.utr, form.knownFactsPostcode, form.name, form.email, form.telephone))
                Redirect(x)
              }
          }
        )
    }
  }

  private def redisplayInitialDetails(
    formWithErrors: Form[InitialDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.subscription_details(knownFactsResult.taxpayerName, formWithErrors))
    }.getOrElse {
      sessionMissingRedirect()
    })

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
    for (wasEligibileForMapping <- sessionStoreService.fetchMappingEligible) yield {
      val redirectLocation = wasEligibileForMapping match {
        case Some(true) => routes.SubscriptionController.showLinkAccount()
        case _          => routes.SubscriptionController.showSubscriptionComplete()
      }

      Redirect(redirectLocation).withSession(request.session + ("arn" -> arn.value))
    }

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
                    subscriptionService.subscribe(details, validDesAddress).flatMap(redirectSubscriptionResponse)
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
                  subscriptionService.subscribe(initialDetails, validDesAddress).flatMap(redirectSubscriptionResponse)
                }
                .getOrElse(Future.successful(sessionMissingRedirect()))
          }
        )
    }
  }

  val showLinkAccount: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedAgent {
      Future.successful {
        request.session.get("arn").fold(sessionMissingRedirect()) { _ =>
          Ok(html.link_account(linkAccountForm))
        }
      }
    }
  }

  val submitLinkAccount: Action[AnyContent] = Action.async { implicit request =>
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
