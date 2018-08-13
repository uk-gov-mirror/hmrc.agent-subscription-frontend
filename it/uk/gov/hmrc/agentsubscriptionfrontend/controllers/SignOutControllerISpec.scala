package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{ChainedSessionDetails, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.{ChainedSessionDetailsRepository, StashedChainedSessionDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.MappingStubs
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class SignOutControllerISpec extends BaseISpec {
  private lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]

  private lazy val sosRedirectUrl = "/government-gateway-registration-frontend?accountType=agent"

  private lazy val repo = app.injector.instanceOf[ChainedSessionDetailsRepository]

  private val fakeRequest = FakeRequest()

  "redirect to SOS" should {
    val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
    def findByUtr(utr: String): Option[StashedChainedSessionDetails] = {
      await(repo.find("chainedSessionDetails.knownFacts.utr" -> utr).map(_.headOption))
    }

    "redirect to SOS page" in {
      val result = await(controller.redirectToSos(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "save the ChainedSessionDetails in the DB" when {

      "was eligible for mapping" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
        MappingStubs.givenMappingEligibilityIsEligible
        MappingStubs.givenMappingCreatePreSubscription(Utr("9876543210"))

        await(controller.redirectToSos(request))
        findByUtr("9876543210").map(_.chainedSessionDetails) shouldBe Some(ChainedSessionDetails(knownFactsResult, wasEligibleForMapping = true))
        MappingStubs.verifyMappingEligibilityCalled(times = 1)
        MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("9876543210"), times = 1)
      }

      "was not eligible for mapping" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
        MappingStubs.givenMappingEligibilityIsNotEligible

        await(controller.redirectToSos(request))
        findByUtr("9876543210").map(_.chainedSessionDetails) shouldBe Some(ChainedSessionDetails(knownFactsResult, wasEligibleForMapping = false))
        MappingStubs.verifyMappingEligibilityCalled(times = 1)
        MappingStubs.verifyMappingCreatePreSubscriptionCalled(Utr("9876543210"), times = 0)
      }
    }

    "check for mapping eligibility call fails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
      MappingStubs.givenMappingEligibilityCheckFails(500)

      an[Upstream5xxResponse] should be thrownBy await(controller.redirectToSos(request))
    }

    "was eligible for mapping but create pre-subscription mapping call fails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
      MappingStubs.givenMappingEligibilityIsEligible
      MappingStubs.givenMappingCreatePreSubscription(Utr("9876543210"), httpReturnCode = 500)

      an[Upstream5xxResponse] should be thrownBy await(controller.redirectToSos(request))
    }

    "the SOS redirect URL should include an ID of the saved ChainedSessionDetails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
      MappingStubs.givenMappingEligibilityIsNotEligible

      val result = await(controller.redirectToSos(request))
      val id = findByUtr("9876543210").map(_.id).get
      redirectLocation(result).head should include(
        s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3D$id")
    }

    "not include an ID in the SOS redirect URL when KnownFactsResults are not yet known" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      MappingStubs.givenMappingEligibilityIsNotEligible

      val result = await(controller.redirectToSos(request))
      redirectLocation(result).head should include(s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created")
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession(hc(request)).continueUrl = Some(ourContinueUrl)
      MappingStubs.givenMappingEligibilityIsNotEligible

      val result = await(controller.redirectToSos(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }

    "include both an ID and a continue URL in the SOS redirect URL if both a continue URL and KnownFacts exist in the session store" in {
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)
      MappingStubs.givenMappingEligibilityIsNotEligible

      val result = await(controller.redirectToSos(request))
      val id = findByUtr("9876543210").map(_.id).get

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?id=$id&continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=$sosContinueValueEncoded"
      redirectLocation(result).head should include(expectedSosContinueParam)
    }
  }

  "start survey" should {
    "redirect to the survey page" in {
      val result = await(controller.startSurvey(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include("feedback-survey")
    }
  }

  "redirect to Agent Services Account page" should {
    "logout and redirect to agent services account" in {
      implicit val request = fakeRequest.withSession("sessionId" -> "SomeSession")

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

    def testLogoutAndRedirect(expectedRedirectUrl: String, maybeContinueUrl: Option[ContinueUrl] = None): Unit = {
      implicit val request = fakeRequest.withSession("sessionId" -> "SomeSession")

      maybeContinueUrl.map{ continueUrl =>
        implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
        sessionStoreService.cacheContinueUrl(continueUrl)
      }

      request.session.get("sessionId") should not be empty

      val result = await(controller.signOutWithContinueUrl(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe expectedRedirectUrl

      result.session.get("sessionId") shouldBe empty
    }
  }
}
