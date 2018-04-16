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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{CallOps, Monitoring}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future
import scala.util.control.NonFatal

case class SubscriptionDetails(utr: Utr,
                               knownFactsPostcode: String,
                               name: String,
                               email: String,
                               telephone: String,
                               address: DesAddress)

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(initDetails: InitialDetails, address: DesAddress): SubscriptionDetails = {
    SubscriptionDetails(initDetails.utr, initDetails.knownFactsPostcode, initDetails.name,
      initDetails.email, initDetails.telephone, address)
  }
}

@Singleton
class SubscriptionController @Inject()
(override val messagesApi: MessagesApi,
 override val authConnector: AuthConnector,
 override val config: PasscodeVerificationConfig,
 override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
 subscriptionService: SubscriptionService,
 sessionStoreService: SessionStoreService,
 addressLookUpConnector: AddressLookupFrontendConnector,
 val continueUrlActions: ContinueUrlActions,
 val metrics: Metrics
)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  private val initialDetailsForm = Form[InitialDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> emailAddress,
      "telephone" -> telephone
    )(InitialDetails.apply)(InitialDetails.unapply)
  )

  private case class SubscriptionReturnedHttpError(httpStatusCode: Int) extends Product with Serializable

  private def hasEnrolments(implicit request: AgentRequest[_]): Boolean = request.enrolments.nonEmpty

  val showInitialDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() { implicit authContext =>
    implicit request =>
      hasEnrolments match {
        case true => Future(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
        case false =>
          mark("Count-Subscription-CleanCreds-Success")
          sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
            Ok(html.subscription_details(knownFactsResult.taxpayerName, initialDetailsForm.fill(
              InitialDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null))))
          }.getOrElse {
            sessionMissingRedirect()
          })
      }
  }

  val submitInitialDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        initialDetailsForm.bindFromRequest().fold(
          formWithErrors =>
            redisplayInitialDetails(formWithErrors),
          form => {
            mark("Count-Subscription-AddressLookup-Start")
            addressLookUpConnector.initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName).map { x =>
              sessionStoreService.cacheInitialDetails(InitialDetails(form.utr, form.knownFactsPostcode, form.name,
                form.email, form.telephone))
              Redirect(x)
            }
          }
        )
  }

  private def redisplayInitialDetails(formWithErrors: Form[InitialDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.subscription_details(knownFactsResult.taxpayerName, formWithErrors))
    }.getOrElse {
      sessionMissingRedirect()
    })

  import SubscriptionDetails._

  private def subscribe(details: InitialDetails,
                        address: DesAddress)
                       (implicit hc: HeaderCarrier): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
    val subscriptionDetails = mapper(details, address)
    subscriptionService.subscribeAgencyToMtd(subscriptionDetails) map {
      case Right(arn) => {
        Right((arn, subscriptionDetails.name))
      }
      case Left(x) => Left(SubscriptionReturnedHttpError(x))
    }
  }

  private def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)]): Result = {
    either match {
      case Right((arn, agencyName)) =>
        mark("Count-Subscription-Complete")
        Redirect(routes.SubscriptionController.showSubscriptionComplete())
        .flashing("arn" -> arn.arn)

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Redirect(routes.CheckAgencyController.showAlreadySubscribed())

      case Left(SubscriptionReturnedHttpError(status)) =>
        mark("Count-Subscription-Failed")
        throw new HttpException("Subscription failed", status)
    }
  }

  def returnFromAddressLookup(id: String): Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>

        sessionStoreService.fetchInitialDetails.flatMap { maybeDetails =>
          maybeDetails.map { details =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              desAddressForm.bindAddressLookupFrontendAddress(details.utr, address).fold(
                formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
                validDesAddress => {
                  mark("Count-Subscription-AddressLookup-Success")
                  subscribe(details, validDesAddress).map(redirectSubscriptionResponse)
                }
              )
            }
          }.getOrElse(Future.successful(sessionMissingRedirect()))
        }
  }

  def submitModifiedAddress: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync() {
    implicit authContext =>
      implicit request =>
        desAddressForm.form.bindFromRequest().fold(
          formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
          validDesAddress =>
            sessionStoreService.fetchInitialDetails.flatMap { maybeInitialDetails =>
              maybeInitialDetails.map { initialDetails =>
                subscribe(initialDetails, validDesAddress).map(redirectSubscriptionResponse)
              }.getOrElse(Future.successful(sessionMissingRedirect()))
            }
        )
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedWithAgentAffinityAsync {
    implicit authContext =>
      implicit request => {
        val arn = request.flash.get("arn")

        arn match {
          case Some(arn) =>
            sessionStoreService.fetchContinueUrl.
              recover { case NonFatal(ex) =>
                Logger.warn("Session store service failure", ex)
                None
              }.
              andThen { case _ => sessionStoreService.remove() }.
              map { continueUrlOpt =>
                val continueUrl = continueUrlOpt.map(_.url).getOrElse(appConfig.agentServicesAccountUrl)
                val isUrlToASAccount = continueUrlOpt.isEmpty
                Ok(html.subscription_complete(continueUrl, isUrlToASAccount, arn))
              }
          case _ =>
            Future.successful(sessionMissingRedirect())
        }
      }
  }
}
