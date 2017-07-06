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
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookUpConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.models.Address
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

case class InitialDetails(utr: Utr,
                          knownFactsPostcode: String,
                          name: String,
                          email: String,
                          telephone: String)


case class SubscriptionDetails(utr: Utr,
                               knownFactsPostcode: String,
                               name: String,
                               email: String,
                               telephone: String,
                               addressLine1: String,
                               addressLine2: Option[String],
                               addressLine3: Option[String],
                               postcode: String)

object SubscriptionDetails {
  implicit val format: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(initDetails: InitialDetails, address: Address): SubscriptionDetails = {
    SubscriptionDetails(initDetails.utr, initDetails.knownFactsPostcode, initDetails.name,
      initDetails.email, initDetails.telephone, address.addressLine1, address.addressLine2,
      address.addressLine3, address.postcode.getOrElse(""))
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
 addressLookUpConnector: AddressLookUpConnector
)
(implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  private val JourneyName: String = appConfig.journeyName
  private var initialDetails: Option[InitialDetails] = None

  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val renderErrors: Set[ValidationError] => String = (errors: Set[ValidationError]) => errors.map(valError =>
    Messages(valError.message, valError.args: _*)).foldLeft("")(_ + ", " + _).substring(1)

  private val subscriptionDetails = Form[InitialDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> email,
      "telephone" -> telephone
    )(InitialDetails.apply)(InitialDetails.unapply)
  )

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
        import Address._

        addressLookUpConnector.getAddressDetails(id).flatMap { address =>
          validate(address, blacklistedPostCodes) match {
            case Invalid(errors) =>

              Future {
                Ok(uk.gov.hmrc.agentsubscriptionfrontend.views.html.des_will_not_accept_address(id, renderErrors(errors)))
              }
            case Valid(()) =>

              subscriptionService.subscribeAgencyToMtd(mapper(initialDetails.get, address)) flatMap { subscriptionResponse =>
                sessionStoreService.remove() map { _ =>
                  subscriptionResponse match {
                    case Right(r) => Redirect(routes.SubscriptionController.showSubscriptionComplete())
                      .flashing("arn" -> r.arn, "agencyName" -> mapper(initialDetails.get, address).name)
                    case Left(CONFLICT) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
                    case Left(FORBIDDEN) => Redirect(routes.SubscriptionController.showSubscriptionFailed())
                    case Left(error) => InternalServerError(s"Unknown error code from agent-subscription $error")
                  }
                }
              }
          }
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
              initialDetails = Some(InitialDetails(form.utr, form.knownFactsPostcode, form.name,
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
          Ok(html.subscription_complete(data._1, data._2))
        ) getOrElse sessionMissingRedirect()
      }
  }
}