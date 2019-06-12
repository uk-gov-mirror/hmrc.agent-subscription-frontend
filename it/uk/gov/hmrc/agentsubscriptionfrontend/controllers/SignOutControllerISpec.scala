package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder
import java.time.LocalDate

import org.scalatest.BeforeAndAfterEach
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.repository.{ChainedSessionDetailsRepository, StashedChainedSessionDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.MappingStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

trait SignOutControllerISpec extends BaseISpec with BeforeAndAfterEach {
  protected def featureFlagAutoMapping: Boolean

  protected lazy val sosRedirectUrl = "/government-gateway-registration-frontend?accountType=agent"
  protected lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]
  protected lazy val repo = app.injector.instanceOf[ChainedSessionDetailsRepository]

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure("features.auto-map-agent-enrolments" -> featureFlagAutoMapping)

  private val fakeRequest = FakeRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.drop)
  }

  def findByUtr(utr: String): Option[StashedChainedSessionDetails] = {
    await(repo.find("chainedSessionDetails.agentSession.utr" -> utr).map(_.headOption))
  }

  "redirectToSos" should {
    "redirect to SOS page" in {
      val result = await(controller.redirectToSos(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "the SOS redirect URL should include an ID of the saved ChainedSessionDetails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

      givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

      val result = await(controller.redirectToSos(request))
      val id = findByUtr("9876543210").map(_.id).get
      redirectLocation(result).head should include(
        s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3D$id")
    }

    "the SOS redirect URL should include an ID of the saved ChainedSessionDetails when initial dsetails is empty" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

      givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

      val result = await(controller.redirectToSos(request))
      val id = findByUtr("9876543210").map(_.id).get
      redirectLocation(result).head should include(
        s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3D$id")
    }

    "not include an ID in the SOS redirect URL when KnownFactsResults are not yet known" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

      val result = await(controller.redirectToSos(request))
      redirectLocation(result).head should include(s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created")
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

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
      sessionStoreService.currentSession.agentSession =
        Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

      val result = await(controller.redirectToSos(request))
      val id = findByUtr("9876543210").map(_.id).get

      val sosContinueValueUnencoded =
        s"/agent-subscription/return-after-gg-creds-created?id=$id&continue=${ourContinueUrl.encodedUrl}"
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

  "signOut" should {
    "logout and redirect to start page" in {
      implicit val request = fakeRequest.withSession("sessionId" -> "SomeSession")
      val result = await(controller.signOut(request))

      status(result) shouldBe 303

      redirectLocation(result).head shouldBe routes.StartController.start().url
      result.session.get("sessionId") shouldBe empty
    }
  }
}

class SignOutControllerWithAutoMappingOn extends SignOutControllerISpec {
  override def featureFlagAutoMapping: Boolean = true

  "redirectToSos" should {
    val amlsDetails = AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))
    "save the ChainedSessionDetails in the DB" when {
      "was eligible for mapping" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        sessionStoreService.currentSession.agentSession =
          Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

        givenMappingCreatePreSubscription(Utr("9876543210"))

        await(controller.redirectToSos(request))
        findByUtr("9876543210").map(_.chainedSessionDetails) shouldBe Some(
          ChainedSessionDetails(
            wasEligibleForMapping = Some(true),
            AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration))
          )
        )
        verifyMappingCreatePreSubscriptionCalled(Utr("9876543210"), times = 1)
      }

      "was not eligible for mapping" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

        givenMappingCreatePreSubscriptionIsNotEligible(Utr("9876543210"))

        await(controller.redirectToSos(request))
        findByUtr("9876543210").map(_.chainedSessionDetails) shouldBe Some(
          ChainedSessionDetails(
            wasEligibleForMapping = Some(false),
            AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration))
          )
        )
        verifyMappingCreatePreSubscriptionCalled(Utr("9876543210"), times = 1)
      }
    }

    "throw an exception if the call to create pre-subscription mapping fails" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))
      givenMappingCreatePreSubscription(Utr("9876543210"), httpReturnCode = 500)

      an[Upstream5xxResponse] should be thrownBy await(controller.redirectToSos(request))
    }
  }
}

class SignOutControllerWithAutoMappingOff extends SignOutControllerISpec {
  override def featureFlagAutoMapping: Boolean = false

  "redirectToSos" should {
    "save the ChainedSessionDetails in the DB with a missing mapping eligibility result" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

      await(controller.redirectToSos(request))
      findByUtr("9876543210").map(_.chainedSessionDetails) shouldBe Some(
        ChainedSessionDetails(
          wasEligibleForMapping = None,
          AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration))
        )
      )
    }

    "not call agent-mapping backend" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      Some(AgentSession(Some(BusinessType.SoleTrader), utr = Some(Utr("9876543210")), registration = Some(registration)))

      await(controller.redirectToSos(request))
      verifyMappingCreatePreSubscriptionCalled(Utr("9876543210"), times = 0)
    }
  }
}
