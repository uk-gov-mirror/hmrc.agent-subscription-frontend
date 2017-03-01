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
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionService
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

case class SubscriptionDetails(name: String,
                               email: String,
                               telephone: String,
                               addressLine1: String,
                               addressLine2: String,
                               addressLine3: Option[String],
                               postcode: String)

@Singleton
class SubscriptionController @Inject()
  (override val messagesApi: MessagesApi,
   override val authConnector: AuthConnector,
   subscriptionService: SubscriptionService
  )
  (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions {

  private val subscriptionDetails = Form[SubscriptionDetails](
    mapping(
      "name" -> nonEmptyText,
      "email" -> nonEmptyText,
      "telephone" -> nonEmptyText,
      "addressLine1" -> nonEmptyText,
      "addressLine2" -> nonEmptyText,
      "addressLine3" -> optional(nonEmptyText),
      "postcode" -> nonEmptyText
    )(SubscriptionDetails.apply)(SubscriptionDetails.unapply)
  )

  val showSubscriptionDetails: Action[AnyContent] = AuthorisedWithSubscribingAgent { implicit authContext => implicit request =>
    Ok(html.subscription_details(subscriptionDetails))
  }

  val submitSubscriptionDetails: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync {
    implicit authContext =>
      implicit request =>
        subscriptionDetails.bindFromRequest().fold(
          formWithErrors => {
            Future successful Ok(html.subscription_details(formWithErrors))
          },
          form => subscriptionService.subscribeAgencyToMtd("0123456789", "AA1 2AA", form) map {
            case Right(_) => Redirect(routes.SubscriptionController.showSubscriptionComplete())
            case Left(CONFLICT) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
            case Left(FORBIDDEN) => Redirect(routes.SubscriptionController.showSubscriptionFailed())
            case Left(error) => InternalServerError(s"Unknown error code from agent-subscription $error")
          })
  }


  val showSubscriptionFailed: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        Ok(html.subscription_failed("Postcodes do not match"))
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
          Ok(html.subscription_complete())
  }
}
