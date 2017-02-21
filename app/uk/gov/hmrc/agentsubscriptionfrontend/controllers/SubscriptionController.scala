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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

@Singleton
class SubscriptionController @Inject()
  (override val messagesApi: MessagesApi, override val authConnector: AuthConnector, val agentSubscriptionConnector: AgentSubscriptionConnector)
  (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions {

  val showSubscriptionDetails: Action[AnyContent] = Action { implicit request =>
    Ok(html.subscription_details())
  }

  val submitSubscriptionDetails: Action[AnyContent] = AuthorisedWithAgent {
    implicit authContext =>
      implicit request =>
          Redirect(routes.SubscriptionController.showSubscriptionComplete())
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedWithAgent {
    implicit authContext =>
      implicit request =>
          Ok(html.subscription_complete())
  }
}
