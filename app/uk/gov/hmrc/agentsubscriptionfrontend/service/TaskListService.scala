/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskListService @Inject()(agentAssuranceConnector: AgentAssuranceConnector, appConfig: AppConfig) {

  def createTasks(subscriptionJourneyRecord: SubscriptionJourneyRecord)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[List[Task]] =
    for {
      maa <- agentAssuranceConnector.isManuallyAssuredAgent(subscriptionJourneyRecord.businessDetails.utr)
    } yield {
      if (isCleanCredsAgent(subscriptionJourneyRecord)) {
        val amlsAndContactDetailsTaskList: List[Task] = amlsAndContactDetailsTasks(subscriptionJourneyRecord, maa)
        val checkAnswersTask: Task = CheckAnswersTask(
          List(CheckAnswersSubTask(amlsAndContactDetailsTaskList.forall(_.isComplete))))
        amlsAndContactDetailsTaskList ::: List(checkAnswersTask)

      } else {

        val amlsAndContactDetailsTaskList: List[Task] = amlsAndContactDetailsTasks(subscriptionJourneyRecord, maa)

        val mappingTask: Task = MappingTask(
          List(
            MappingSubTask(
              subscriptionJourneyRecord.cleanCredsAuthProviderId,
              subscriptionJourneyRecord.mappingComplete,
              subscriptionJourneyRecord.continueId.getOrElse(" "),
              amlsAndContactDetailsTaskList.forall(_.isComplete),
              appConfig
            )
          )
        )
        val createIDTask: Task = CreateIDTask(
          List(CreateIDSubTask(subscriptionJourneyRecord.cleanCredsAuthProviderId, mappingTask.isComplete)))
        val checkAnswersTask: Task = CheckAnswersTask(List(CheckAnswersSubTask(createIDTask.isComplete)))
        amlsAndContactDetailsTaskList ::: List(mappingTask, createIDTask, checkAnswersTask)
      }
    }

  def isCleanCredsAgent(subscriptionJourneyRecord: SubscriptionJourneyRecord) =
    subscriptionJourneyRecord.cleanCredsAuthProviderId.contains(subscriptionJourneyRecord.authProviderId)

  private def amlsAndContactDetailsTasks(
    subscriptionJourneyRecord: SubscriptionJourneyRecord,
    maa: Boolean): List[Task] = {

    val amlsTask: Task = AmlsTask(List(AmlsSubTask(maa, subscriptionJourneyRecord.amlsData)))

    val contactEmailSubTask: SubTask = ContactDetailsEmailSubTask(
      subscriptionJourneyRecord.contactEmailData,
      amlsTask.isComplete
    )
    val contactTradingNameSubTask: SubTask = ContactTradingNameSubTask(
      subscriptionJourneyRecord.contactDetailsTradingName,
      contactEmailSubTask.isComplete
    )
    val contactTradingAddressSubTask: SubTask = ContactTradingAddressSubTask(
      subscriptionJourneyRecord.contactDetailsTradingAddress,
      contactTradingNameSubTask.isComplete
    )
    val contactDetailsTask: Task = ContactDetailsTask(
      List(
        contactEmailSubTask,
        contactTradingNameSubTask,
        contactTradingAddressSubTask
      )
    )
    List(amlsTask, contactDetailsTask)
  }

}
