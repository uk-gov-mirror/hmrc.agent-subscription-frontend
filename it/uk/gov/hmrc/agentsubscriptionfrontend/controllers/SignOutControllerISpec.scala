package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.repository.{ChainedSessionDetailsRepository, StashedChainedSessionDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class SignOutControllerISpec extends BaseISpec {

  protected lazy val sosRedirectUrl = "/government-gateway-registration-frontend?accountType=agent"
  protected lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]

  private val fakeRequest = FakeRequest()

  trait TestSetup {
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = Some("foo")))
  }

  "redirectToSos" should {
    "redirect to SOS page" in new TestSetup {
      val result = await(controller.redirectToSos(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "the SOS redirect URL should include an ID of the saved continue id" in new TestSetup {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.redirectToSos(request))
      redirectLocation(result).head should include(
        s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3Dfoo")
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(continueId = None))

      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      val result = await(controller.redirectToSos(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }

    "include both an ID and a continue URL in the SOS redirect URL if both a continue URL and KnownFacts exist in the session store" in new TestSetup {
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      val result = await(controller.redirectToSos(request))

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?id=foo&continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }
  }

  "startSurvey" should {
    "redirect to the survey page" in {
      val result = await(controller.startSurvey(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include("feedback-survey")
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
      testLogoutAndRedirect(expectedRedirectUrl = "/gg/sign-in")
    }

    "logout and redirect to /gg/sign-in?continue=... when continue URL is present in the session" in {
      testLogoutAndRedirect(
        expectedRedirectUrl = "/gg/sign-in?continue=%2Ftest-continue-url",
        maybeContinueUrl = Some(ContinueUrl("/test-continue-url"))
      )
    }

    def testLogoutAndRedirect(expectedRedirectUrl: String, maybeContinueUrl: Option[ContinueUrl] = None): Assertion = {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")

      maybeContinueUrl.map{ continueUrl =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
        sessionStoreService.cacheContinueUrl(continueUrl)
      }

      request.session.get("sessionId") should not be empty

      val result = await(controller.signOutWithContinueUrl(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedRedirectUrl

      result.session.get("sessionId") shouldBe empty
    }
  }

  "signOut" should {
    "logout and redirect to start page" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withSession("sessionId" -> "SomeSession")
      val result = await(controller.signOut(request))

      status(result) shouldBe 303

      redirectLocation(result).head shouldBe routes.StartController.start().url
      result.session.get("sessionId") shouldBe empty
    }
  }
}
