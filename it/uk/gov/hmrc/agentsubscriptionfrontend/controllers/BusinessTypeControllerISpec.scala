package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import org.jsoup.Jsoup
import play.api.test.Helpers.{LOCATION, defaultAwaitTimeout, redirectLocation}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{subscribingAgentEnrolledForNonMTD, subscribingCleanAgentWithoutEnrolments}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.validBusinessTypes

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessTypeControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: BusinessTypeController = app.injector.instanceOf[BusinessTypeController]


  "redirectToBusinessTypeForm" should {
    "redirect to the business type form page" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.redirectToBusinessTypeForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }
  }

  "showBusinessTypeForm (GET /business-type)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showBusinessTypeForm(_))

          behave like aPageTakingContinueUrlAndCachingInSessionStore(
            controller.showBusinessTypeForm(_),
            userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))

          "contain page titles and header content" in new TestSetupNoJourneyRecord {
            val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
            val result = await(controller.showBusinessTypeForm(request))

            result should containMessages(
              "businessType.title",
        "businessType.progressive.title",
        "businessType.progressive.content.p1")
    }

    "contain radio options for Sole Trader, Limited Company, Partnership, and LLP" in new TestSetupNoJourneyRecord{
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      doc.getElementById("businessType-sole_trader").`val`() shouldBe "sole_trader"
      doc.getElementById("businessType-limited_company").`val`() shouldBe "limited_company"
      doc.getElementById("businessType-partnership").`val`() shouldBe "partnership"
      doc.getElementById("businessType-llp").`val`() shouldBe "llp"
    }

    "contain a link to sign out" in new TestSetupNoJourneyRecord{
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))
      val signOutLink = doc.getElementById("sign-out")
      signOutLink.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutLink.text() shouldBe htmlEscapedMessage("businessType.progressive.content.link")
    }

    "pre-populate the business type if one is already stored in the session" in new TestSetupNoJourneyRecord{
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.showBusinessTypeForm()(request))

      val doc = Jsoup.parse(bodyOf(result))
      val link = doc.getElementById("businessType-sole_trader")
      link.attr("checked") shouldBe "checked"
    }

    "redirect to task list if a subscription journey exists for the logged in user" in {
      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"),
        TestData.minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }
  }

  "submitBusinessTypeForm (POST /business-type)" when {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitBusinessTypeForm(_))

    validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"redirect to /business-details when valid businessTypeIdentifier: $validBusinessTypeIdentifier" in new TestSetupNoJourneyRecord{
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withFormUrlEncodedBody("businessType" -> validBusinessTypeIdentifier.key)

        val result = await(controller.submitBusinessTypeForm(request))
        //result.header.headers(LOCATION) shouldBe routes.UtrController.showUtrForm().url
        result.header.headers(LOCATION) shouldBe routes.BusinessDetailsController.showBusinessDetailsForm().url
      }
    }

    "choice is invalid" should {
      "return 200 and redisplay the /business-type page with an error message for invalid choice - the user manipulated the submit value" in new TestSetupNoJourneyRecord{
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("businessType" -> "invalid")
        val result = await(controller.submitBusinessTypeForm(request))
        result should containMessages("businessType.error.invalid-choice")
      }
    }
  }

}
