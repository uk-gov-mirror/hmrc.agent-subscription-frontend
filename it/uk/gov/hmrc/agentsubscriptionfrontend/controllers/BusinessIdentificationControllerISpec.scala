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

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessAddress
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.id
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData, TestSetupNoJourneyRecord}

class BusinessIdentificationControllerISpec extends BaseISpec {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  val utr = Utr("2000000000")
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  trait Setup {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
      subscribingCleanAgentWithoutEnrolments)
    givenAgentIsNotManuallyAssured(utr.value)
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id))
  }

  "show existing journey found for utr route" should {
    "display warning page" in new TestSetupNoJourneyRecord {
      val result = await(controller.showExistingJourneyFound(authenticatedAs(subscribingAgentEnrolledForNonMTD)))
      result should containMessages("existingJourneyFound.p1")
    }
  }

  "showCreateNewAccount" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCreateNewAccount(request))
    behave like aPageWithFeedbackLinks(controller.showCreateNewAccount(_), authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the has other enrolments page if the current user is logged in and has affinity group = Agent" in new TestSetupNoJourneyRecord{
      val result = await(controller.showCreateNewAccount(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      result should containMessages("createNewAccount.title")
    }
  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoMatchFound(request))
    behave like aPageWithFeedbackLinks(request => {
      controller.showNoMatchFound(request)
    }, authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in new TestSetupNoJourneyRecord{
      val result = await(controller.showNoMatchFound(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("noAgencyFound.title", "noAgencyFound.p1", "noAgencyFound.p2", "button.startAgain")
    }
  }

  "showAlreadySubscribed" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showAlreadySubscribed(request))

    "display the already subscribed page if the current user is logged in and has affinity group = Agent" in new TestSetupNoJourneyRecord{

      val result = await(controller.showAlreadySubscribed(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("alreadySubscribed.title")
      result should containLink("button.finishSignOut", routes.SignedOutController.redirectToBusinessTypeForm().url)
    }
  }

  "GET /change-business-name changeBusinessName" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.changeBusinessName(_))
    "contain page with expected content" in new Setup {

      val result = await(controller.changeBusinessName(authenticatedRequest))

      result should containMessages(
        "businessName.title"
      )
      result should containSubmitButton("button.saveContinue", "business-name-change-continue")
      result should containSubmitButton("button.saveComeBackLater", "business-name-change-save")
    }

    "pre-populate the business name data into the form when it is present in the BE store" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)

      val result = await(controller.changeBusinessName(authenticatedRequest))

      result should containSubstrings("My Agency")
    }
  }

    "POST /change-business-name changeBusinessName" should {
      behave like anAgentAffinityGroupOnlyEndpoint(controller.submitChangeBusinessName(_))

      "redirect to /check-answers when user inputs valid data and continues" in new Setup {
        givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)
        givenSubscriptionRecordCreated(id,TestData.completeJourneyRecordWithUpdatedBusinessName("New Agency Name"))

        val result = await(
          controller.submitChangeBusinessName(
            authenticatedRequest.withFormUrlEncodedBody("name" -> "New Agency Name", "continue" -> "continue")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.SubscriptionController.showCheckAnswers.url)
      }

      "redirect to /progress-saved when user inputs valid data and saves" in new Setup {
        givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)
        givenSubscriptionRecordCreated(id,TestData.completeJourneyRecordWithUpdatedBusinessName("New Agency Name"))

        val result = await(
          controller.submitChangeBusinessName(
            authenticatedRequest.withFormUrlEncodedBody("name" -> "New Agency Name", "continue" -> "save")))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(
          routes.TaskListController.savedProgress(
            backLink = Some(routes.BusinessIdentificationController.changeBusinessName().url)).url)
      }
    }

  "GET /change-business-email changeBusinessEmail" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.changeBusinessEmail(_))
    "contain page with expected content" in new Setup {

      val result = await(controller.changeBusinessEmail(authenticatedRequest))

      result should containMessages(
        "businessEmail.title",
        "businessEmail.description"
      )

      result should containSubmitButton("button.saveContinue", "business-email-change-continue")
      result should containSubmitButton("button.saveComeBackLater", "business-email-change-save")
    }

    "pre-populate the business email data into the form when it is present in the BE store" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)

      val result = await(controller.changeBusinessEmail(authenticatedRequest))

      result should containSubstrings("test@gmail.com")
    }
  }

  "POST /change-business-email changeBusinessEmail" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitChangeBusinessEmail(_))

    "redirect to /check-answers when user inputs valid data and continues" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)
      givenSubscriptionRecordCreated(id,TestData.completeJourneyRecordWithUpdatedBusinessEmail("new@gmail.com"))

      val result = await(
        controller.submitChangeBusinessEmail(
          authenticatedRequest.withFormUrlEncodedBody("email" -> "new@gmail.com", "continue" -> "continue")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showCheckAnswers.url)
    }

    "redirect to /progress-saved when user inputs valid data and saves" in new Setup {
      givenSubscriptionJourneyRecordExists(id, TestData.completeJourneyRecordNoMappings)
      givenSubscriptionRecordCreated(id,TestData.completeJourneyRecordWithUpdatedBusinessEmail("new@gmail.com"))

      val result = await(
        controller.submitChangeBusinessEmail(
          authenticatedRequest.withFormUrlEncodedBody("email" -> "new@gmail.com", "continue" -> "save")))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(
        routes.TaskListController.savedProgress(
          backLink = Some(routes.BusinessIdentificationController.changeBusinessEmail().url)).url)
    }
  }



}
