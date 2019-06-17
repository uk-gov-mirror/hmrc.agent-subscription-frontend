package uk.gov.hmrc.agentsubscriptionfrontend.controllers.business

import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{BusinessIdentificationController, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, validUtr, _}

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessAddressISpec extends BaseISpec {
  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display update business address form with pre-filled values" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

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

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "submitUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business address after submission" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "new addressline 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.AMLSController.showCheckAmlsPage().url

      val updatedBusinessAddress = await(sessionStoreService.fetchAgentSession).get.registration.get.address

      updatedBusinessAddress.addressLine1 shouldBe "new addressline 1"
      updatedBusinessAddress.addressLine2 shouldBe Some("new addressline 2")
      updatedBusinessAddress.addressLine3 shouldBe Some("new addressline 3")
      updatedBusinessAddress.addressLine4 shouldBe Some("new addressline 4")
      updatedBusinessAddress.postalCode shouldBe Some("BB11BB")
    }

    "show validation error when the form is submitted with empty address line 1" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> " ",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB")

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.empty")
    }

    "show validation error when the form is submitted with invalid address line 3" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline **!",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_3.title", "error.addressline.3.invalid")
    }

    "show validation error when the form is submitted with invalid address line 1" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1**",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.invalid")
    }

    "show validation error when the form is submitted with postcode which exceed max length" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BBBBBBBBBBBBBBB"
        )

      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.postcode.title", "error.postcode.maxlength")
    }

    "redirect to postcode-not-allowed page" when {
      "postcode entered is blacklisted" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> blacklistedPostcode
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode entered is BFPO" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> "BF11XX"
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode starts with BFPO" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
            "addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode"     -> "BFPO15"
          )

        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), registration = Some(registration)))

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

        val result = await(controller.showBusinessNameForm(request))

        redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
      }
    }
  }
}
