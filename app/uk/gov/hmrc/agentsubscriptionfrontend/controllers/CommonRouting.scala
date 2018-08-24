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
import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.StoreEligibility
import uk.gov.hmrc.agentsubscriptionfrontend.models.StoreEligibility.{IsEligible, IsNotEligible, MappingUnavailable}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonRouting @Inject()(sessionStoreService: SessionStoreService, appConfig: AppConfig) {

  private[controllers] def redirectUponSuccessfulSubscription(
    arn: Arn)(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
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
}
