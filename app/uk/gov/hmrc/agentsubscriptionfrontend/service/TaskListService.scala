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
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, TaskListFlags}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskListService @Inject()(agentAssuranceConnector: AgentAssuranceConnector) {

  def getTaskListFlags(subscriptionJourneyRecord: SubscriptionJourneyRecord)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[TaskListFlags] =
    for {
      manuallyAssured <- isMaaAgent(subscriptionJourneyRecord.businessDetails.utr)
    } yield
      TaskListFlags(
        amlsTaskComplete = isAmlsTaskComplete(subscriptionJourneyRecord),
        isMAA = manuallyAssured,
        createTaskComplete = isCreateTaskComplete(subscriptionJourneyRecord),
        checkAnswersComplete = isCheckAnswersComplete(subscriptionJourneyRecord)
      )

  private def isMaaAgent(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    agentAssuranceConnector.isManuallyAssuredAgent(utr)

  private def isAmlsTaskComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean =
    subscriptionJourneyRecord.amlsData.fold(false)(_ => true)

  private def isCreateTaskComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean =
    subscriptionJourneyRecord.cleanCredsInternalId.fold(false)(_ => true)

  private def isCheckAnswersComplete(subscriptionJourneyRecord: SubscriptionJourneyRecord): Boolean =
    subscriptionJourneyRecord.subscriptionCreated

}
