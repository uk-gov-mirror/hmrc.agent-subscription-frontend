package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.{givenAUserDoesNotHaveRelationshipInCesa, givenNinoAGoodCombinationAndUserHasRelationshipInCesa, givenUtrAGoodCombinationAndUserHasRelationshipInCesa}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{registration, validUtr, _}
import uk.gov.hmrc.domain.Nino

class InvasiveChecksControllerISpec extends BaseISpec {

  lazy val controller: InvasiveChecksController = app.injector.instanceOf[InvasiveChecksController]

  "post invasive check" should {
    "return 200 and redisplay the invasiveSaAgentCodePost page with an error message for missing radio choice" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.invasiveSaAgentCodePost(request))
      Messages("invasive.error.no-radio.selected").r.findAllMatchIn(bodyOf(result)).size shouldBe 2
    }

    "start invasiveCheck if selected Yes with SaAgentCode" when {

      "input contains only capital letters" in { testSaAgentCodeCheck("SA6012") }
      "input contains letters in mixed case" in { testSaAgentCodeCheck("sA6012") }
      "input contains letters in lower case" in { testSaAgentCodeCheck("sa6012") }

      def testSaAgentCodeCheck(sageAgentCode: String) = {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", sageAgentCode))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.invasiveSaAgentCodePost(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.InvasiveChecksController.showClientDetailsForm().url)
        noMetricExpectedAtThisPoint()
      }
    }

    "redirect to /cannot-create account if selected No" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("hasSaAgentCode", "false"))
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.invasiveSaAgentCodePost(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "return 200 and display page with error when failing the validation of SaAgentCode" when {
      "it contains invalid characters" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA601*2AAAA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.invalid")
        result should repeatMessage("error.saAgentCode.invalid", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong max length" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012AAAA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)
        val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong min length" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA"))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.invasiveSaAgentCodePost(request))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "contains empty SaAgentCode" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", ""))
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.invasiveSaAgentCodePost(request))

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
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", nino))
          .withSession("saAgentReferenceToCheck" -> "SA6012")

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

        val result = await(controller.submitClientDetailsForm(request))

        status(result) shouldBe 303
        //redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012", true)
        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
      }
    }

    "redirect to invasive check start when no SACode in session to obtain it again" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration.copy(emailAddress = None))))

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.InvasiveChecksController.invasiveCheckStart().url)
    }

    "redirect to /cannot-create account page when submitting valid nino with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration)))

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), false, "SA6012", true)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "clientDetails no variant selected" in {

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", ""))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("clientDetails.error.no-radio.selected"))
    }

    "nino invalid send back 200 with error page" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123"))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.invalid"))
    }

    "nino empty send back 200 with error page" in {

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", ""))

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.nino.empty"))
    }

    "redirect to confirm business when successfully submitting UTR" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")

      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(utr = Some(validUtr), postcode = Some(Postcode(validPostcode))))

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      //redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", true)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm business when successfully submitting UTR with random spaces" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "   40000      00     009  "))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(utr = Some(validUtr), postcode = Some(Postcode(validPostcode))))

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 303
      //redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)
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
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "cannotProvide"))
          .withSession("saAgentReferenceToCheck" -> "SA6012")
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.submitClientDetailsForm(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
      }
    }

    "utr blank invasiveCheck" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", ""))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.blank"))
    }

    "utr invalid send back 200 with error page" in {

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4ABC000009"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "utr wrong length" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40000000090000000"))
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "return 200 error when submitting without selected radio option" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody()
        .withSession("saAgentReferenceToCheck" -> "SA6012")
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.submitClientDetailsForm(request))

      status(result) shouldBe 200
    }
  }

}
