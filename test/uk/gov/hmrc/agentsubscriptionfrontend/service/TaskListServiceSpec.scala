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

import java.time.LocalDate

import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TaskListServiceSpec extends UnitSpec with MockitoSugar {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.global

  private val stubAssuranceConnector = mock[AgentAssuranceConnector]

  private val stubAppConfig = mock[AppConfig]

  private def givenNotManuallyAssured: OngoingStubbing[Future[Boolean]] =
    when(stubAssuranceConnector.isManuallyAssuredAgent(Utr("12345")))
      .thenReturn(Future.successful(false))

  private def givenManuallyAssured: OngoingStubbing[Future[Boolean]] =
    when(stubAssuranceConnector.isManuallyAssuredAgent(Utr("12345")))
      .thenReturn(Future.successful(true))

  private val taskListService = new TaskListService(stubAssuranceConnector, stubAppConfig)

  val minimalUncleanCredsRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    None,
    None
  )

  val minimalCleanCredsRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    Some(AuthProviderId("cred-1234")),
    None
  )

  "when the user has unclean creds show the full task list" should {
    "AmlsTask" should {
      "when agent is not manually assured show AMLS link" in {
        givenNotManuallyAssured
        val tasks = await(taskListService.createTasks(minimalUncleanCredsRecord))

        tasks.length shouldBe 4
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.showLink shouldBe true
      }

      "when agent is manually assured don't show AMLS link" in {
        givenManuallyAssured
        val tasks = await(taskListService.createTasks(minimalUncleanCredsRecord))

        tasks.length shouldBe 4
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.showLink shouldBe false
      }

      "when agent has registered AMLS data show AMLS as complete" in {
        givenNotManuallyAssured
        val amlsRecord = minimalUncleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Right(RegisteredDetails("123", LocalDate.now().plusDays(20))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 4
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent has pending AMLS data show AMLS as complete" in {
        givenNotManuallyAssured
        val amlsRecord = minimalUncleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 4
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent is manually assured show AMLS as complete" in {
        givenManuallyAssured
        val tasks = await(taskListService.createTasks(minimalUncleanCredsRecord))
        tasks.length shouldBe 4
        tasks.head.isComplete shouldBe true
      }

      "when agent has partially completed AMLS details show AMLS as not complete" in {
        givenNotManuallyAssured
        val partialAmlsRecord =
          minimalUncleanCredsRecord.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None)))
        val tasks = await(taskListService.createTasks(partialAmlsRecord))

        tasks.length shouldBe 4
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe false
      }
    }

    "MappingTask" should {
      "when agent has no clean creds auth provider id and the previous task is complete show the mapping task link" in {
        givenNotManuallyAssured
        val record = minimalUncleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))))
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 4
        tasks(1).taskKey shouldBe "mappingTask"
        tasks(1).showLink shouldBe true
      }

      "when an agent has completed mapping and the previous task is complete show the mapping task as complete" in {
        givenManuallyAssured
        val record = minimalUncleanCredsRecord.copy(mappingComplete = true)
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 4
        tasks(1).taskKey shouldBe "mappingTask"
        tasks(1).isComplete shouldBe true
      }
    }

    "CreateIDTask" should {
      "when the previous task is complete show the create id link" in {
        givenNotManuallyAssured
        val record = minimalUncleanCredsRecord.copy(
          mappingComplete = true,
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20)))))))
        )
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 4
        tasks(2).taskKey shouldBe "createIDTask"
        tasks(2).showLink shouldBe true
      }

      "when an agent has an auth provider id and the previous task is complete show the create id task as complete" in {
        givenManuallyAssured
        val record = minimalUncleanCredsRecord
          .copy(cleanCredsAuthProviderId = Some(AuthProviderId("cred-123")), mappingComplete = true)
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 4
        tasks(2).taskKey shouldBe "createIDTask"
        tasks(2).isComplete shouldBe true
      }
    }

    "CheckAnswersTask" should {
      "when an agent has completed the previous task show the check answers link" in {
        givenManuallyAssured
        val record = minimalUncleanCredsRecord
          .copy(cleanCredsAuthProviderId = Some(AuthProviderId("cred-123")), mappingComplete = true)
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 4
        tasks(3).taskKey shouldBe "checkAnswersTask"
        tasks(3).showLink shouldBe true
      }
    }
  }

  "when the user has clean creds show a reduced task list" should {
    "AmlsTask" should {
      "when agent is not manually assured show AMLS link" in {
        givenNotManuallyAssured
        val tasks = await(taskListService.createTasks(minimalCleanCredsRecord))

        tasks.length shouldBe 2
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.showLink shouldBe true
      }

      "when agent is manually assured don't show AMLS link" in {
        givenManuallyAssured
        val tasks = await(taskListService.createTasks(minimalCleanCredsRecord))

        tasks.length shouldBe 2
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.showLink shouldBe false
      }

      "when agent has registered AMLS data show AMLS as complete" in {
        givenNotManuallyAssured
        val amlsRecord = minimalCleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Right(RegisteredDetails("123", LocalDate.now().plusDays(20))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 2
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent has pending AMLS data show AMLS as complete" in {
        givenNotManuallyAssured
        val amlsRecord = minimalCleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))))
        val tasks = await(taskListService.createTasks(amlsRecord))

        tasks.length shouldBe 2
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe true
      }

      "when agent is manually assured show AMLS as complete" in {
        givenManuallyAssured
        val tasks = await(taskListService.createTasks(minimalCleanCredsRecord))
        tasks.length shouldBe 2
        tasks.head.isComplete shouldBe true
      }

      "when agent has partially completed AMLS details show AMLS as not complete" in {
        givenNotManuallyAssured
        val partialAmlsRecord =
          minimalCleanCredsRecord.copy(amlsData = Some(AmlsData(amlsRegistered = true, None, None)))
        val tasks = await(taskListService.createTasks(partialAmlsRecord))

        tasks.length shouldBe 2
        tasks.head.taskKey shouldBe "amlsTask"
        tasks.head.isComplete shouldBe false
      }
    }

    "CheckAnswersTask" should {
      "when an agent has completed the previous task show the check answers link" in {
        givenManuallyAssured
        val record = minimalCleanCredsRecord.copy(
          amlsData = Some(
            AmlsData(
              amlsRegistered = true,
              None,
              Some(AmlsDetails("supervisory", Left(PendingDetails(LocalDate.now().minusDays(20))))))))
        val tasks = await(taskListService.createTasks(record))

        tasks.length shouldBe 2
        tasks(1).taskKey shouldBe "checkAnswersTask"
        tasks(1).showLink shouldBe true
      }
    }
  }
}
