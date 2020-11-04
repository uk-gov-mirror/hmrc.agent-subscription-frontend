package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import org.scalatest.Assertion
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, SessionLost, TestData}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.ExecutionContext.Implicits.global

class SignOutControllerISpec extends BaseISpec {

  protected lazy val sosRedirectUrl = "/government-gateway-registration-frontend?accountType=agent"
  protected lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]

  private val fakeRequest = FakeRequest()

  trait TestSetup {
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = Some("foo")))
  }

  "redirectToSos" should {

    "redirect user to create clean creds" in new TestSetup {
      private val result = await(controller.redirectAgentToCreateCleanCreds(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "the SOS redirect URL should include an ID of the saved continue id" in new TestSetup {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      private val result = await(controller.redirectAgentToCreateCleanCreds(request))
      redirectLocation(result).head should include(
        s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3Dfoo")
    }

    def assertContinueUrl(result: Result, continueUrl: String): Assertion = {
      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?continue=${URLEncoder.encode(continueUrl, "UTF-8")}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {

      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = None))

      val ourContinueUrl = "/test-continue-url"
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      assertContinueUrl(
        await(controller.redirectAgentToCreateCleanCreds(authenticatedAs(subscribingAgentEnrolledForNonMTD))),
        ourContinueUrl
      )
    }

    "include both an ID and a continue URL in the SOS redirect URL if both a continue URL and KnownFacts exist in the session store" in new TestSetup {
      val ourContinueUrl = "/test-continue-url"
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      private val result = await(controller.redirectAgentToCreateCleanCreds(request))

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?id=foo&continue=${URLEncoder.encode(ourContinueUrl, "UTF-8")}"
      val sosContinueValueEncoded: String = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }
  }

  "startSurvey" should {
    "redirect to the survey page" in {
      val result = await(controller.startSurvey(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include("feedback/AGENTSUB")
    }
  }

  "redirectToASAccountPage" should {
    "logout and redirect to agent services account" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")

      request.session.get("sessionId") should not be empty

      val result = await(controller.redirectToASAccountPage(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include("agent-services-account")

      result.session.get("sessionId") shouldBe empty
    }
  }

  "signOutWithContinueUrl" should {

    "logout and redirect to /gg/sign-in when no continue URL is present in the session" in {
      testLogoutAndRedirect(expectedRedirectUrl = "http://localhost:9025/gg/sign-in")
    }

    "logout and redirect to /gg/sign-in?continue=... when continue URL is present in the session" in {
      testLogoutAndRedirect(
        expectedRedirectUrl = "http://localhost:9025/gg/sign-in?continue=%2Ftest-continue-url",
        maybeContinueUrl = Some("/test-continue-url")
      )
    }

    "sign out to continueUrl with expired session should just sign out; not give an exception" in {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")

      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

      sessionStoreService.cacheContinueUrl(RedirectUrl("/someContinueUrl"))

      request.session.get("sessionId") should not be empty

      sessionStoreService.currentSessionTest = SessionLost // simulate SessionCache expiry

      val result = await(controller.signOutWithContinueUrl(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe "/agent-subscription/task-list"

    }

    def testLogoutAndRedirect(expectedRedirectUrl: String, maybeContinueUrl: Option[String] = None): Assertion = {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")

      maybeContinueUrl.map{ continueUrl =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
        sessionStoreService.cacheContinueUrl(RedirectUrl(continueUrl))
      }

      request.session.get("sessionId") should not be empty

      val result = await(controller.signOutWithContinueUrl(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedRedirectUrl

      result.session.get("sessionId") shouldBe empty
    }
  }

  "signOut" should {
    "logout and redirect to task list page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val result = await(controller.signOut(request))

      status(result) shouldBe 303

      redirectLocation(result).head shouldBe routes.TaskListController.showTaskList().url
      result.session.get("sessionId") shouldBe empty
    }
  }

  "timedOut" should {
    "show the timed out page with forbidden status" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest
      val result = await(controller.timedOut(request))

      status(result) shouldBe 403
      checkHtmlResultWithBodyText(result, "You have been signed out", "Sign in again")

    }
  }

  "redirectToBusinessTypeForm" should {
    "redirect to the business type page and remove the session" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val result = await(controller.redirectToBusinessTypeForm(request))

      status(result) shouldBe 303

      redirectLocation(result).head shouldBe routes.BusinessTypeController.showBusinessTypeForm().url
      result.session.get("sessionId") shouldBe empty
    }
  }
}
