package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, SampleUser, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, validUtr, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.{givenSubscriptionRecordCreated, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessAddressISpec extends BaseISpec {
  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display update business address form with pre-filled values" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.showUpdateBusinessAddressForm(request))
      result should containMessages(
        "updateBusinessAddress.title",
        "updateBusinessAddress.p1",
        "updateBusinessAddress.p2",
        "updateBusinessAddress.address_line_1.title",
        "updateBusinessAddress.address_line_2.title",
        "updateBusinessAddress.address_line_3.title",
        "updateBusinessAddress.address_line_4.title",
        "updateBusinessAddress.postcode.title",
        "updateBusinessAddress.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("addressLine1").`val` shouldBe businessAddress.addressLine1
      doc.getElementById("addressLine2").`val` shouldBe businessAddress.addressLine2.get
      doc.getElementById("addressLine3").`val` shouldBe businessAddress.addressLine3.get
      doc.getElementById("addressLine4").`val` shouldBe businessAddress.addressLine4.get
      doc.getElementById("postcode").`val` shouldBe businessAddress.postalCode.get

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitUpdateBusinessAddressForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business address after submission, redirect to task list when there is a continueUrl" in {
      val agentSession = AgentSession(
        Some(BusinessType.SoleTrader),
        utr = Some(validUtr),
        registration = Some(testRegistration),
        postcode = Some(Postcode("AA11AA")))

      val authId = AuthProviderId("12345-credId")
      givenNoSubscriptionJourneyRecordExists(authId)
      withMatchingUtrAndPostcode(validUtr, "AA11AA")

      val sjr = SubscriptionJourneyRecord.fromAgentSession(agentSession, authId)
      val newSjr = sjr.copy(
        continueId = None, // can't match this, because it is randomly generated
        businessDetails = sjr.businessDetails.copy(
          registration = Some(testRegistration.copy(
            address = new BusinessAddress(
              addressLine1 = "new addressline 1",
              addressLine2 = Some("new addressline 2"),
              addressLine3 = Some("new addressline 3"),
              addressLine4 = Some("new addressline 4"),
              postalCode    = Some("BB11BB"),
              countryCode = "GB"
      )))))

      givenSubscriptionRecordCreated(authId, newSjr)

      givenAgentIsNotManuallyAssured(validUtr.value)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "new addressline 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )

      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitUpdateBusinessAddressForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url

      val updatedBusinessAddress = await(sessionStoreService.fetchAgentSession).get.registration.get.address

      updatedBusinessAddress.addressLine1 shouldBe "new addressline 1"
      updatedBusinessAddress.addressLine2 shouldBe Some("new addressline 2")
      updatedBusinessAddress.addressLine3 shouldBe Some("new addressline 3")
      updatedBusinessAddress.addressLine4 shouldBe Some("new addressline 4")
      updatedBusinessAddress.postalCode shouldBe Some("BB11BB")
    }

    "show validation error when the form is submitted with empty address line 1" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> " ",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB")

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.empty")
    }

    "show validation error when the form is submitted with invalid address line 3" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline **!",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_3.title", "error.addressline.3.invalid")
    }

    "show validation error when the form is submitted with invalid address line 1" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1**",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.invalid")
    }

    "show validation error when the form is submitted with postcode which exceed max length" in new TestSetupNoJourneyRecord {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BBBBBBBBBBBBBBB"
        )

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.postcode.title", "error.postcode.maxlength")
    }

    "redirect to postcode-not-allowed page" when {
      "postcode entered is blacklisted" in new TestSetupNoJourneyRecord {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> blacklistedPostcode
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode entered is BFPO" in new TestSetupNoJourneyRecord {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> "BF11XX"
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode starts with BFPO" in new TestSetupNoJourneyRecord {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> "BFPO15"
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(testRegistration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

        val result = await(controller.showBusinessNameForm(request))

        redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
      }
    }
  }
}
