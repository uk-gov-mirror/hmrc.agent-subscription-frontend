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
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.IsEligible
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonRouting @Inject()(
  mappingConnector: MappingConnector,
  subscriptionService: SubscriptionService,
  appConfig: AppConfig) {

  private[controllers] def handleAutoMapping(eligibleForMapping: Option[Boolean])(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    MappingEligibility.apply(eligibleForMapping) match {
      case IsEligible if appConfig.autoMapAgentEnrolments =>
        toFuture(Redirect(routes.SubscriptionController.showLinkClients()))
      case _ => toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
    }

  private[controllers] def withCleanCreds(agent: Agent)(f: => Future[Result]): Future[Result] =
    agent match {
      case hasNonEmptyEnrolments(_) =>
        toFuture(Redirect(routes.BusinessIdentificationController.showCreateNewAccount()))
      case _ => f
    }
}
