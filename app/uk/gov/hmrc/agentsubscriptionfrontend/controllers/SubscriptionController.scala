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

import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.passcode.authentication.{PasscodeAuthenticationProvider, PasscodeVerificationConfig}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

case class SubscriptionDetails(utr: Utr,
                               knownFactsPostcode: String,
                               name: String,
                               email: String,
                               telephone: String,
                               addressLine1: String,
                               addressLine2: Option[String],
                               addressLine3: Option[String],
                               postcode: String)

@Singleton
class SubscriptionController @Inject()
  (override val messagesApi: MessagesApi,
   override val authConnector: AuthConnector,
   override val config: PasscodeVerificationConfig,
   override val passcodeAuthenticationProvider: PasscodeAuthenticationProvider,
   subscriptionService: SubscriptionService,
   sessionStoreService: SessionStoreService
  )
  (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions with SessionDataMissing {

  private val subscriptionDetails = Form[SubscriptionDetails](
    mapping(
      "utr" -> utr,
      "knownFactsPostcode" -> postcode,
      "name" -> agencyName,
      "email" -> email,
      "telephone" -> telephone,
      "addressLine1" -> addressLine1,
      "addressLine2" -> addressLine23,
      "addressLine3" -> addressLine23,
      "postcode" -> postcode(appConfig.blacklistedPostcodes)
    )(SubscriptionDetails.apply)(SubscriptionDetails.unapply)
  )

  val showSubscriptionDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync { implicit authContext => implicit request =>
      sessionStoreService.fetchKnownFactsResult.map(_.map { knownFactsResult =>
        Ok(html.subscription_details(knownFactsResult.taxpayerName, subscriptionDetails.fill(
          SubscriptionDetails(knownFactsResult.utr, knownFactsResult.postcode, null, null, null, null, None, None, null))))
      }.getOrElse {
        sessionMissingRedirect()
      })
    }

  val submitSubscriptionDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
    implicit authContext =>
      implicit request =>
        subscriptionDetails.bindFromRequest().fold(
          formWithErrors =>
            redisplaySubscriptionDetails(formWithErrors),
          form => subscriptionService.subscribeAgencyToMtd(form) flatMap { subscriptionResponse =>
            sessionStoreService.remove() map { _ =>
              subscriptionResponse match {
                case Right(r) => Redirect(routes.SubscriptionController.showSubscriptionComplete())
                  .flashing("arn" -> r.arn, "agencyName" -> form.name)
                case Left(CONFLICT) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
                case Left(FORBIDDEN) => Redirect(routes.SubscriptionController.showSubscriptionFailed())
                case Left(error) => InternalServerError(s"Unknown error code from agent-subscription $error")
              }
            }
          })
  }

  private def redisplaySubscriptionDetails(formWithErrors: Form[SubscriptionDetails])(implicit hc: HeaderCarrier, request: Request[_]) =
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

        agencyData.map (data =>
          Ok(html.subscription_complete(data._1, data._2))
        ) getOrElse sessionMissingRedirect()
      }
  }
}
