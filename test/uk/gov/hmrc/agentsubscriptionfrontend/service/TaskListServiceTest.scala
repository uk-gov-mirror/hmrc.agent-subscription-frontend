package uk.gov.hmrc.agentsubscriptionfrontend.service

import java.time.LocalDate

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AuthProviderId, BusinessType, Postcode, TaskListFlags}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TaskListServiceTest extends UnitSpec with MockitoSugar {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.global

  private val stubAssuranceConnector = mock[AgentAssuranceConnector]

  when(stubAssuranceConnector.isManuallyAssuredAgent(Utr("12345")))
    .thenReturn(Future.successful(false))

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
    "show amls incomplete when no amls details are entered" in {
      val flags = await(taskListService.getTaskListFlags(minimalRecord))
      flags should be(TaskListFlags())
    }

    "show amls incomplete when some (incomplete) amls details are entered - registered true" in {
      val data = Some(AmlsData(amlsRegistered = true, None, None, None, None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
    }

    "show amls complete when all amls details are entered - registered true" in {
      val data = Some(AmlsData(amlsRegistered = true, None, Some("HMRC"), None, Some(RegDetails("mem", LocalDate.now()))))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags(amlsTaskComplete = true))
    }

    "show amls incomplete when some (incomplete) amls details are entered - registered false" in {
      val data = Some(AmlsData(amlsRegistered = false, None, None, None, None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
    }

    "show amls incomplete when amls is not applied for" in {
      val data = Some(AmlsData(amlsRegistered = false, Some(false), None, None, None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags())
    }

    "show amls complete when all amls details are entered - registered false" in {
      val data = Some(AmlsData(amlsRegistered = false, Some(true), Some("HMRC"), Some(PendingDate(LocalDate.now())), None))
      val flags = await(taskListService.getTaskListFlags(minimalRecord.copy(amlsData = data)))
      flags should be(TaskListFlags(amlsTaskComplete = true))
    }

  }

}
