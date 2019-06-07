package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import org.jsoup.Jsoup
import play.api.test.Helpers.LOCATION
import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.validBusinessTypes
import scala.concurrent.ExecutionContext.Implicits.global

class BusinessTypeControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: BusinessTypeController = app.injector.instanceOf[BusinessTypeController]

  "showBusinessTypeForm (GET /business-type)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showBusinessTypeForm(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(
      controller.showBusinessTypeForm(_),
      userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))

    "contain page titles and header content" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      result should containMessages(
        "businessType.title",
        "businessType.progressive.title",
        "businessType.progressive.content.p1")
    }

    "contain radio options for Sole Trader, Limited Company, Partnership, and LLP" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      doc.getElementById("businessType-sole_trader").`val`() shouldBe "sole_trader"
      doc.getElementById("businessType-limited_company").`val`() shouldBe "limited_company"
      doc.getElementById("businessType-partnership").`val`() shouldBe "partnership"
      doc.getElementById("businessType-llp").`val`() shouldBe "llp"
    }

    "contain a link to sign out" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))
      val signOutLink = doc.getElementById("sign-out")
      signOutLink.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutLink.text() shouldBe htmlEscapedMessage("businessType.progressive.content.link")
    }

    "pre-populate the business type if one is already stored in the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.showBusinessTypeForm()(request))

      val doc = Jsoup.parse(bodyOf(result))
      val link = doc.getElementById("businessType-sole_trader")
      link.attr("checked") shouldBe "checked"
    }

  }

  "submitBusinessTypeForm (POST /business-type)" when {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitBusinessTypeForm(_))

    validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"redirect to /business-details when valid businessTypeIdentifier: $validBusinessTypeIdentifier" in {
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withFormUrlEncodedBody("businessType" -> validBusinessTypeIdentifier.key)

        val result = await(controller.submitBusinessTypeForm(request))
        //result.header.headers(LOCATION) shouldBe routes.UtrController.showUtrForm().url
        result.header.headers(LOCATION) shouldBe routes.BusinessDetailsController.showBusinessDetailsForm().url
      }
    }

    "choice is invalid" should {
      "return 200 and redisplay the /business-type page with an error message for invalid choice - the user manipulated the submit value" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("businessType" -> "invalid")
        val result = await(controller.submitBusinessTypeForm(request))
        result should containMessages("businessType.error.invalid-choice")
      }
    }
  }

}
