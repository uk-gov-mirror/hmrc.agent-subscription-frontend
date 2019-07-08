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

import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.{givenAgentIsManuallyAssured, givenAgentIsNotManuallyAssured}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingCleanAgentWithoutEnrolments, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class AMLSControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: AMLSController = app.injector.instanceOf[AMLSController]

  val utr = Utr("0123456789")
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  trait Setup {
    implicit val authenticatedRequest = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
    sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr)))
    givenAgentIsNotManuallyAssured(utr.value)
  }

  trait SetupUnclean {
    implicit val authenticatedRequest = authenticatedAs(subscribingAgentEnrolledForNonMTD)
    sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr)))
    givenAgentIsNotManuallyAssured(utr.value)
  }



  "GET /check-money-laundering-compliance" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showCheckAmlsPage(_))

    "contain page with expected content" in new Setup {
      val result = await(controller.showCheckAmlsPage(authenticatedRequest))

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no"
      )
    }
  }

  "POST /check-money-laundering-compliance" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitCheckAmls(_))

    "redirect to /money-laundering-compliance when user selects yes" in new Setup {
      val result = await(controller.submitCheckAmls(authenticatedRequest.withFormUrlEncodedBody("registeredAmls" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsDetailsForm().url)
    }

    "redirect to /check-money-laundering-application when user selects no" in new Setup {
      val result = await(controller.submitCheckAmls(authenticatedRequest.withFormUrlEncodedBody("registeredAmls" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showCheckAmlsAlreadyAppliedForm().url)
    }

    "handle form with errors - user does not make a choice and tries to continue" in new Setup {
      val result = await(controller.submitCheckAmls(authenticatedRequest.withFormUrlEncodedBody("registeredAmls" -> "")))

      status(result) shouldBe 200

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no",
        "error.check-amls-value.invalid"
      )
    }

    "handle form with errors - user manipulates the value and tries to continue" in new Setup {
      val result = await(controller.submitCheckAmls(authenticatedRequest.withFormUrlEncodedBody("registeredAmls" -> "blah")))

      status(result) shouldBe 200

      result should containMessages(
        "check-amls.title",
        "button.yes",
        "button.no",
        "error.check-amls-value.invalid"
      )
    }
  }


  "GET /check-money-laundering-application" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showCheckAmlsAlreadyAppliedForm(_))

    "contain page with expected content" in new Setup {
      val result = await(controller.showCheckAmlsAlreadyAppliedForm(authenticatedRequest))

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no"
      )
    }
  }

  "POST /check-money-laundering-application" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitCheckAmlsAlreadyAppliedForm(_))

    "redirect to /money-laundering-application-details when user selects yes" in new Setup {
      val result = await(controller.submitCheckAmlsAlreadyAppliedForm(authenticatedRequest.withFormUrlEncodedBody("amlsAppliedFor" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsApplicationDatePage().url)
    }

    "redirect to /money-laundering-compliance-incomplete when user selects no" in new Setup {
      val result = await(controller.submitCheckAmlsAlreadyAppliedForm(authenticatedRequest.withFormUrlEncodedBody("amlsAppliedFor" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsNotAppliedPage().url)
    }

    "handle form with errors - user does not make a choice and tries to continue" in new Setup {
      val result = await(controller.submitCheckAmlsAlreadyAppliedForm(authenticatedRequest.withFormUrlEncodedBody("amlsAppliedFor" -> "")))

      status(result) shouldBe 200

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no",
        "error.check-amlsAppliedFor-value.invalid"
      )
    }

    "handle form with errors - user manipulates the value and tries to continue" in new Setup {
      val result = await(controller.submitCheckAmlsAlreadyAppliedForm(authenticatedRequest.withFormUrlEncodedBody("amlsAppliedFor" -> "blah")))

      status(result) shouldBe 200

      result should containMessages(
        "amlsAppliedFor.title",
        "button.yes",
        "button.no",
        "error.check-amlsAppliedFor-value.invalid"
      )
    }
  }

  "showAmlsDetailsForm (GET /money-laundering-compliance)" should {

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showAmlsDetailsForm(_))

    "contain page titles and header content" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      result should containMessages(
        "moneyLaunderingCompliance.title",
        "moneyLaunderingCompliance.p1"
      )
    }

    "ask for a money laundering supervisory body name from a list of acceptable values" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))
      result should containMessages("moneyLaunderingCompliance.amls.title")

      val doc = Jsoup.parse(bodyOf(result))

      val elAmlsSelect = doc.getElementById("amls-auto-complete")
      elAmlsSelect should not be null
      elAmlsSelect.tagName() shouldBe "select"

      val amlsBodies = AMLSLoader.load("/amls.csv")
      amlsBodies.foreach{
        case (expectedCode, expectedName) => {
          val elChoice = elAmlsSelect.getElementById(s"amlsCode-$expectedCode")
          elChoice should not be null
          elChoice.tagName() shouldBe "option"
          elChoice.attr("value") shouldBe expectedCode
          elChoice.text() shouldBe expectedName
        }
      }
    }

    "ask for membership number" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      result should containMessages("moneyLaunderingCompliance.membershipNumber.title")
      result should containInputElement("membershipNumber", "text")
    }

    "ask for membership expiry date" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      result should containMessages(
        "moneyLaunderingCompliance.expiry.title",
        "moneyLaunderingCompliance.expiry.hint",
        "moneyLaunderingCompliance.expiry.day.title",
        "moneyLaunderingCompliance.expiry.month.title",
        "moneyLaunderingCompliance.expiry.year.title"
      )
      result should containInputElement("expiry.day", "tel")
      result should containInputElement("expiry.month", "tel")
      result should containInputElement("expiry.year", "tel")
    }

    "contain a continue button" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      result should containSubmitButton(
        expectedMessageKey = "moneyLaunderingCompliance.continue",
        expectedElementId = "continue"
      )
    }

    "contain a form that would POST to /money-laundering-compliance" in new Setup {
      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))
      val doc = Jsoup.parse(bodyOf(result))

      val elForm = doc.select("form")
      elForm should not be null
      elForm.attr("action") shouldBe "/agent-subscription/money-laundering-compliance"
      elForm.attr("method") shouldBe "POST"
    }

    "redirect to /check-answers page if the agent is manually assured" in {
      implicit val authenticatedRequest = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr)))
      givenAgentIsManuallyAssured(utr.value)

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showAmlsDetailsForm(request))

      resultShouldBeSessionDataMissing(result)
    }

    "pre-populate amls form if they are coming from /check_answers and also go to /check_answers page when user clicks on 'Go Back' link" in new Setup {

      //pre-state
      val amlsDetails = AMLSDetails("Insolvency Practitioners Association (IPA)", Right(RegisteredDetails("123456789", LocalDate.now())))
      sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr), amlsDetails = Some(amlsDetails)))
      sessionStoreService.currentSession.goBackUrl = Some(routes.SubscriptionController.showCheckAnswers().url)

      val result = await(controller.showAmlsDetailsForm(authenticatedRequest))

      contentAsString(result) should (
        include ("""<a href="/agent-subscription/check-answers" class="link-back">Back</a>""")
        and include ("""selected="selected">Insolvency Practitioners Association (IPA)</option>""")
        and include ("""value="123456789"""")
        and include (s"""value="${LocalDate.now().getYear.toString}""""))
    }
  }

  "submitAmlsDetailsForm (POST /money-laundering-compliance)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitAmlsDetailsForm(_))

    val expiryDate = LocalDate.now().plusDays(2)
    val expiryDay = expiryDate.getDayOfMonth.toString
    val expiryMonth = expiryDate.getMonthValue.toString
    val expiryYear = expiryDate.getYear.toString

    "store AMLS form in session cache after successful submission, and redirect to check answers when there is a continue url" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)
      sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/continue/url"))

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url

      val amlsDetails = await(sessionStoreService.fetchAgentSession).get.amlsDetails.get

      amlsDetails shouldBe AMLSDetails("Association of AccountingTechnicians (AAT)", Right(RegisteredDetails("12345", expiryDate)))
    }

    "store AMLS form in session cache after successful submission, and redirect to task list when there is no continue url" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url

      val amlsDetails = await(sessionStoreService.fetchAgentSession).get.amlsDetails.get
      val taskListFlags = await(sessionStoreService.fetchAgentSession).get.taskListFlags

      amlsDetails shouldBe AMLSDetails("Association of AccountingTechnicians (AAT)", Right(RegisteredDetails("12345", expiryDate)))
      taskListFlags.createTaskComplete shouldBe true
    }

    "createTaskComplete flag should be false when user has unclean creds" in new SetupUnclean {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url

      val taskListFlags = await(sessionStoreService.fetchAgentSession).get.taskListFlags
      taskListFlags.createTaskComplete shouldBe false
    }

    "show validation error when the form is submitted with empty amlsCode" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.amls.title", "error.moneyLaunderingCompliance.amlscode.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with invalid amlsCode" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "Invalid Text",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.amls.title", "error.moneyLaunderingCompliance.amlscode.invalid")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty membership number" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.membershipNumber.title", "error.moneyLaunderingCompliance.membershipNumber.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with invalid expiry date" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> "123", "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.date.invalid")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty day field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> "", "expiry.month" -> expiryMonth,  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.day.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty month field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> "",  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.month.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty year field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> expiryMonth,  "expiry.year" -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.year.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty day and month field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> "", "expiry.month" -> "",  "expiry.year" -> expiryYear)

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.day.month.empty")
      result shouldNot containMessages("error.moneyLaunderingCompliance.day.empty", "error.moneyLaunderingCompliance.month.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty day and year field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> "", "expiry.month" -> expiryMonth,  "expiry.year" -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.day.year.empty")
      result shouldNot containMessages("error.moneyLaunderingCompliance.day.empty", "error.moneyLaunderingCompliance.year.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty month and year field" in new Setup {
      implicit val request = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "membershipNumber" -> "12345", "expiry.day" -> expiryDay, "expiry.month" -> "",  "expiry.year" -> "")

      val result = await(controller.submitAmlsDetailsForm(request))
      status(result) shouldBe 200
      result should containMessages("moneyLaunderingCompliance.expiry.title", "error.moneyLaunderingCompliance.month.year.empty")
      result shouldNot containMessages("error.moneyLaunderingCompliance.month.empty", "error.moneyLaunderingCompliance.year.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "redirect to /check-answers page if the agent is manually assured" in {
      implicit val authenticatedRequest = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr)))
      givenAgentIsManuallyAssured(utr.value)

      val result = await(controller.submitAmlsDetailsForm(authenticatedRequest))

      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.submitAmlsDetailsForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "GET /money-laundering-compliance-incomplete" should {

    "display page with correct content" in new Setup {

      val result = await(controller.showAmlsNotAppliedPage(authenticatedRequest))

      result should containMessages(
        "amls-not-applied.title",
        "amls-not-applied.p1",
        "amls-not-applied.finish"
      )

      result should containSubstrings("To find details of supervisory bodies, see",
        "anti-money laundering registration (opens in a new window or tab)."
      )
    }
  }

  "GET  /money-laundering-application-details" should {

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showAmlsApplicationDatePage(_))

    "display page with correct content" in new Setup {


      val result = await(controller.showAmlsApplicationDatePage(authenticatedRequest))

      result should containMessages(
        "amls.pending.appliedOn.title",
        "amls.pending.appliedOn.title"
      )
    }
  }

  "POST /money-laundering-application-details" should {

    val appliedOnDate = LocalDate.now().minusMonths(1)
    val day = appliedOnDate.getDayOfMonth.toString
    val month = appliedOnDate.getMonthValue.toString
    val year = appliedOnDate.getYear.toString

    "store AMLS pending details in session cache after successful submission, redirect to check answeres when there is a continue url" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> day, "appliedOn.month" -> month,  "appliedOn.year" -> year)
      sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/continue/url"))

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url

      val amlsDetails = await(sessionStoreService.fetchAgentSession).get.amlsDetails.get

      amlsDetails shouldBe AMLSDetails("Association of AccountingTechnicians (AAT)", Left(PendingDetails(appliedOnDate)))
    }

    "store AMLS pending details in session cache after successful submission, redirect to task list when there is no continue url" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> day, "appliedOn.month" -> month,  "appliedOn.year" -> year)

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.TaskListController.showTaskList().url

      val amlsDetails = await(sessionStoreService.fetchAgentSession).get.amlsDetails.get

      amlsDetails shouldBe AMLSDetails("Association of AccountingTechnicians (AAT)", Left(PendingDetails(appliedOnDate)))
    }

    "show validation error when the form is submitted with empty day field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> "", "appliedOn.month" -> month,  "appliedOn.year" -> year)

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("amls.pending.appliedOn.title", "error.amls.pending.appliedOn.day.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with invalid appliedOn date" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> "123", "appliedOn.month" -> month,  "appliedOn.year" -> year)

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.moneyLaunderingCompliance.date.invalid")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty month field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> day, "appliedOn.month" -> "",  "appliedOn.year" -> year)

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.month.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty year field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> day, "appliedOn.month" -> month,  "appliedOn.year" -> "")

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.year.empty")

      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty day and month field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> "", "appliedOn.month" -> "",  "appliedOn.year" -> year)

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.day.month.empty")
      result shouldNot containMessages("error.amls.pending.appliedOn.day.empty", "error.amls.pending.appliedOn.month.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty day and year field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> "", "appliedOn.month" -> month,  "appliedOn.year" -> "")

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.day.year.empty")
      result shouldNot containMessages("error.amls.pending.appliedOn.day.empty", "error.amls.pending.appliedOn.year.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "show validation error when the form is submitted with empty month and year field" in new Setup {
      implicit val requst = authenticatedRequest.withFormUrlEncodedBody("amlsCode" -> "AAT",
        "appliedOn.day" -> day, "appliedOn.month" -> "",  "appliedOn.year" -> "")

      val result = await(controller.submitAmlsApplicationDatePage(requst))
      status(result) shouldBe 200
      result should containMessages("error.amls.pending.appliedOn.month.year.empty")
      result shouldNot containMessages("error.amls.pending.appliedOn.month.empty", "error.amls.pending.appliedOn.year.empty")
      await(sessionStoreService.fetchAgentSession).get.amlsDetails shouldBe empty
    }

    "redirect to /check-answers page if the agent is manually assured" in {
      implicit val authenticatedRequest = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(businessType = Some(SoleTrader), utr = Some(utr)))
      givenAgentIsManuallyAssured(utr.value)

      val result = await(controller.submitAmlsApplicationDatePage(authenticatedRequest))

      status(result) shouldBe 303
      redirectLocation(result).get shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "redirect to the /business-type page if there is no business type in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.submitAmlsApplicationDatePage(request))

      resultShouldBeSessionDataMissing(result)
    }

  }

}
