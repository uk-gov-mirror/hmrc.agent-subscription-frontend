/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.data.validation.ValidationError
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

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
 addressLookUpValidator: AddressValidator,
 addressLookUpConnector: AddressLookupFrontendConnector
)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  private val subscriptionDetails = Form[InitialDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> email,
      "telephone" -> telephone
    )(InitialDetails.apply)(InitialDetails.unapply)
  )

  private  case class SubscriptionReturnedHttpError(httpStatusCode: Int)  extends Product with Serializable

  private def hasEnrolments(implicit request: AgentRequest[_]): Boolean = request.enrolments.nonEmpty

  val showSubscriptionDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync { implicit authContext =>
    implicit request =>
      hasEnrolments match {
        case true => Future(Redirect(routes.CheckAgencyController.showHasOtherEnrolments()))
        case false => sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
          Ok(html.subscription_details(knownFactsResult.taxpayerName, subscriptionDetails.fill(
            InitialDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null))))
        }.getOrElse {
          sessionMissingRedirect()
        })
      }
  }

  def submit(id: String): Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
    implicit authContext =>
      implicit request =>

        import SubscriptionDetails._

        def subscribe(details: InitialDetails,
                      address: DesAddress, addressLookupAddress: AddressLookupFrontendAddress): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
          val subscriptionDetails = mapper(details, address)
          subscriptionService.subscribeAgencyToMtd(subscriptionDetails) map {
            case Right(arn) => {
              Right((arn, subscriptionDetails.name))
            }
            case Left(x) => Left(SubscriptionReturnedHttpError(x))
          }
        }

        def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)]): Result = {
          either match {
            case Right((arn, agencyName)) => Redirect(routes.SubscriptionController.showSubscriptionComplete())
              .flashing("arn" -> arn.arn, "agencyName" -> agencyName)
            case Left(SubscriptionReturnedHttpError(CONFLICT)) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
            case Left(SubscriptionReturnedHttpError(_)) => Redirect(routes.SubscriptionController.showSubscriptionFailed())
          }
        }

        sessionStoreService.fetchInitialDetails.flatMap { maybeDetails =>
          maybeDetails.map { details =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              addressLookUpValidator.validateAddress(details.utr, address, blacklistedPostCodes) match {
                case Invalid(errors) =>
                  Future.successful(
                    Ok(des_will_not_accept_address(id, SubscriptionController.renderErrors(errors)))
                  )
                case Valid(desAddress) =>
                  val subscriptResponse = for {
                    res ← subscribe(details, desAddress, address)
                    _ ← sessionStoreService.remove()
                  } yield res
                  subscriptResponse.map(redirectSubscriptionResponse)
              }
            }
          }.getOrElse(Future.successful(sessionMissingRedirect()))
        }
  }

  def beginJourney(id: String): Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
    implicit authContext =>
      implicit request =>

        addressLookUpConnector.initJourney(routes.SubscriptionController.submit(), JourneyName).map { x => Redirect(x) }
  }

  val getAddressDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
    implicit authContext =>
      implicit request =>
        subscriptionDetails.bindFromRequest().fold(
          formWithErrors =>
            redisplaySubscriptionDetails(formWithErrors),
          form =>
            addressLookUpConnector.initJourney(routes.SubscriptionController.submit(), JourneyName).map { x =>
              sessionStoreService.cacheInitialDetails(InitialDetails(form.utr, form.knownFactsPostcode, form.name,
                form.email, form.telephone))
              Redirect(x)
            }
        )
  }

  private def redisplaySubscriptionDetails(formWithErrors: Form[InitialDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
    sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
      Ok(html.subscription_details(knownFactsResult.taxpayerName, formWithErrors))
    }.getOrElse {
      sessionMissingRedirect()
    })

  val showSubscriptionFailed: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        Ok(html.subscription_failed("Postcodes do not match"))
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request => {
        val agencyData = for {
          agencyName <- request.flash.get("agencyName")
          arn <- request.flash.get("arn")
        } yield (agencyName, arn)

        agencyData.map(data =>
          Ok(html.subscription_complete(appConfig.redirectUrl, data._1, data._2))
        ) getOrElse sessionMissingRedirect()
      }
  }
}

object SubscriptionController {

  def renderErrors(errors: NonEmptyList[ValidationError])(implicit messages: Messages): String = errors
    .toList
    .map(valError => Messages(valError.message, valError.args: _*))
    .reduce(_ + ", " + _)

}
