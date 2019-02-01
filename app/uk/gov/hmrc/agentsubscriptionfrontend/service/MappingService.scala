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

package uk.gov.hmrc.agentsubscriptionfrontend.service
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.MappingConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility
import uk.gov.hmrc.agentsubscriptionfrontend.models.MappingEligibility.UnknownEligibility
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent._

@Singleton
class MappingService @Inject()(
  appConfig: AppConfig,
  mappingConnector: MappingConnector,
  sessionStoreService: SessionStoreService,
  chainedSessionRepository: ChainedSessionDetailsRepository) {
  def captureTempMappingsPreSubscription(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[MappingEligibility] =
    if (appConfig.autoMapAgentEnrolments) {
      for {
        knownFactsOpt <- sessionStoreService.fetchKnownFactsResult
        mappingEligibility <- knownFactsOpt
                               .map(knownFacts => mappingConnector.createPreSubscription(knownFacts.utr))
                               .getOrElse(Future.successful(UnknownEligibility))
      } yield mappingEligibility
    } else {
      Future.successful(UnknownEligibility)
    }
}
