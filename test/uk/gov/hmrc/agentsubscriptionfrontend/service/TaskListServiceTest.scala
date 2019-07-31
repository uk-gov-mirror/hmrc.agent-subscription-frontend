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

import java.time.LocalDate

import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, AuthProviderId, BusinessType, PendingDetails, Postcode, RegisteredDetails, TaskListFlags}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TaskListServiceTest extends UnitSpec with MockitoSugar {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.global

  private val stubAssuranceConnector = mock[AgentAssuranceConnector]

  private def givenNotManuallyAssured: OngoingStubbing[Future[Boolean]] =
    when(stubAssuranceConnector.isManuallyAssuredAgent(Utr("12345")))
      .thenReturn(Future.successful(false))

  private def givenManuallyAssured: OngoingStubbing[Future[Boolean]] =
    when(stubAssuranceConnector.isManuallyAssuredAgent(Utr("12345")))
      .thenReturn(Future.successful(true))

  private val taskListService = new TaskListService(stubAssuranceConnector)

  val minimalRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    None,
    subscriptionCreated = false,
    None
  )

  "task list service" should {

    "show amls complete when agent is manually assured" in {
      givenManuallyAssured
      val flags = await(taskListService.getTaskListFlags(minimalRecord))
      flags should be(TaskListFlags(isMAA = true, amlsTaskComplete = true))
      flags.mustCreateCleanCreds should be(true) // next task
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(true)
    }

    "show amls incomplete when no amls details are entered" in {
      givenNotManuallyAssured
      val flags = await(taskListService.getTaskListFlags(minimalRecord))
      flags should be(TaskListFlags())
      flags.mustCreateCleanCreds should be(false)
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "show amls incomplete when some (incomplete) amls details are entered - registered true" in {
      givenNotManuallyAssured
      val data = Some(AmlsData(amlsRegistered = true, None, None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
      flags.mustCreateCleanCreds should be(false)
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "show amls complete when all amls details are entered - registered true" in {
      givenNotManuallyAssured
      val data =
        Some(
          AmlsData(
            amlsRegistered = true,
            None,
            Some(AmlsDetails("HMRC", Right(RegisteredDetails("mem", LocalDate.now()))))))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags(amlsTaskComplete = true))
      flags.mustCreateCleanCreds should be(true) // next task
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "show amls incomplete when some (incomplete) amls details are entered - registered false" in {
      givenNotManuallyAssured
      val data = Some(AmlsData(amlsRegistered = false, None, None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
      flags.mustCreateCleanCreds should be(false)
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "show amls incomplete when amls is not applied for" in {
      givenNotManuallyAssured
      val data = Some(AmlsData(amlsRegistered = false, Some(false), None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
      flags.mustCreateCleanCreds should be(false)
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "show amls complete when all amls details are entered - registered false" in {
      givenNotManuallyAssured
      val data =
        Some(
          AmlsData(
            amlsRegistered = false,
            Some(true),
            Some(AmlsDetails("HMRC", Left(PendingDetails(LocalDate.now()))))))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags(amlsTaskComplete = true))
      flags.mustCreateCleanCreds should be(true) // next task
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(false)
    }

    "cannot edit amls once check answers is complete" in {
      givenNotManuallyAssured
      val data =
        Some(
          AmlsData(
            amlsRegistered = true,
            None,
            Some(AmlsDetails("HMRC", Right(RegisteredDetails("mem", LocalDate.now()))))))
      val flags = await(
        taskListService.getTaskListFlags(
          minimalRecord
            .copy(amlsData = data, subscriptionCreated = true, cleanCredsAuthProviderId = Some(AuthProviderId("test")))
        ))
      flags should be(TaskListFlags(amlsTaskComplete = true, createTaskComplete = true, checkAnswersComplete = true))
      flags.mustCreateCleanCreds should be(false)
      flags.mustCheckAnswers should be(false)
      flags.cannotEditAmls should be(true) // they have checked answers and created sub
    }

  }

}
