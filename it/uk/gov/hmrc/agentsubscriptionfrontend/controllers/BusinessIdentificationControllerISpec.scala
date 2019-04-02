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
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.domain.Nino

class BusinessIdentificationControllerISpec extends BaseISpec {

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showCreateNewAccount" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCreateNewAccount(request))
    behave like aPageWithFeedbackLinks(controller.showCreateNewAccount(_), authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the has other enrolments page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showCreateNewAccount(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      result should containMessages("createNewAccount.title")
    }
  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoMatchFound(request))
    behave like aPageWithFeedbackLinks(request => {
      controller.showNoMatchFound(request)
    }, authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showNoMatchFound(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("noAgencyFound.title", "noAgencyFound.p1", "noAgencyFound.p2", "button.startAgain")
    }
  }

  "showAlreadySubscribed" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showAlreadySubscribed(request))

    "display the already subscribed page if the current user is logged in and has affinity group = Agent" in {

      val result = await(controller.showAlreadySubscribed(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("alreadySubscribed.title")
      val doc = Jsoup.parse(bodyOf(result))
      val signOutButton = doc.getElementById("finishSignOut")
      signOutButton.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutButton.text() shouldBe htmlEscapedMessage("button.finishSignOut")

    }
  }

  "post invasive check" should {
    "return 200 and redisplay the invasiveSaAgentCodePost page with an error message for missing radio choice" in {
      val result = await(controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))
      Messages("invasive.error.no-radio.selected").r.findAllMatchIn(bodyOf(result)).size shouldBe 2
    }

    "start invasiveCheck if selected Yes with SaAgentCode" when {

      "input contains only capital letters" in { testSaAgentCodeCheck("SA6012") }
      "input contains letters in mixed case" in { testSaAgentCodeCheck("sA6012") }
      "input contains letters in lower case" in { testSaAgentCodeCheck("sa6012") }

      def testSaAgentCodeCheck(sageAgentCode: String) = {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", sageAgentCode))))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showClientDetailsForm().url)
        noMetricExpectedAtThisPoint()
      }
    }

    "redirect to /cannot-create account if selected No" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "false"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "return 200 and display page with error when failing the validation of SaAgentCode" when {
      "it contains invalid characters" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA601*2AAAA"))))

        result should containMessages("error.saAgentCode.invalid")
        result should repeatMessage("error.saAgentCode.invalid", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong max length" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012AAAA"))))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong min length" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA"))))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "contains empty SaAgentCode" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", ""))))

        result should containMessages("error.saAgentCode.blank")
        result should repeatMessage("error.saAgentCode.blank", 2)
        noMetricExpectedAtThisPoint()
      }
    }
  }

  "POST /client-details form" should {

    "redirect to confirm business when successfully submitting nino" when {

      "input nino contains only capital letters" in { testInvasiveCheckWithNino("AA123456A") }
      "input nino contains mixed case letters" in { testInvasiveCheckWithNino("Aa123456a") }
      "input nino contains only lowercase letters" in { testInvasiveCheckWithNino("aa123456a") }
      "input nino contains random spaces" in { testInvasiveCheckWithNino("AA1   2 3 4 5 6        A ") }

      def testInvasiveCheckWithNino(nino: String) = {
        givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

        val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "nino"), ("nino", nino))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012", true)
        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
      }
    }

    "redirect to invasive check start when no SACode in session to obtain it again" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration.copy(emailAddress = None))))

      val result = await(
        controller.submitClientDetailsForm(request
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
    }

    "redirect to /cannot-create account page when submitting valid nino with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), false, "SA6012", true)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "clientDetails no variant selected" in {
      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", ""))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("clientDetails.error.no-radio.selected"))
    }

    "nino invalid send back 200 with error page" in {
      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123"))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.invalid"))
    }

    "nino empty send back 200 with error page" in {

      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", ""))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.empty"))
    }

    "redirect to confirm business when successfully submitting UTR" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr) , postcode = Some(Postcode(postcode)), registration = Some(registration.copy(emailAddress = None))))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", true)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm business when successfully submitting UTR with random spaces" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "   40000      00     009  "))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", true)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to /cannot-create account page" when {
      "submitting invalid Utr which fails Modulus11Check" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

        val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000019"))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
      }

      "submitting valid utr with no relationship" in {
        givenAUserDoesNotHaveRelationshipInCesa("utr", "40000     00  009", "SA6012")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

        val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), false, "SA6012", true)
        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
      }

      "successfully selecting ICannotProvideEitherOfTheseDetails" in {
        givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

        val result = await(
          controller.submitClientDetailsForm(
            authenticatedAs(subscribingCleanAgentWithoutEnrolments)
              .withFormUrlEncodedBody(("variant", "cannotProvide"))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
      }
    }

    "utr blank invasiveCheck" in {

      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", ""))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.blank"))
    }

    "utr invalid send back 200 with error page" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4ABC000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "utr wrong length" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40000000090000000"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "return 200 error when submitting without selected radio option" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody()
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
    }
  }
}
