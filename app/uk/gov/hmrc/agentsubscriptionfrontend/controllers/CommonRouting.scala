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

import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture

import scala.concurrent.Future

trait CommonRouting {

  val appConfig: AppConfig

  private[controllers] def withCleanCreds(agent: Agent)(f: => Future[Result]): Future[Result] =
    agent match {
      case hasNonEmptyEnrolments(_) =>
        toFuture(Redirect(routes.BusinessIdentificationController.showCreateNewAccount()))
      case _ => f
    }
}
