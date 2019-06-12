package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.agentsubscriptionfrontend.repository.StashedChainedSessionDetails.StashedChainnedSessionId
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.individual
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

trait StartControllerISpec extends BaseISpec {
  protected def featureFlagAutoMapping: Boolean

  protected lazy val controller: StartController = app.injector.instanceOf[StartController]
  protected lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"
  protected lazy val repo = app.injector.instanceOf[ChainedSessionDetailsRepository]

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure("government-gateway.url" -> configuredGovernmentGatewayUrl, "features.auto-map-agent-enrolments" -> featureFlagAutoMapping)

  object FixturesForReturnAfterGGCredsCreated {

    val amlsSDetails = AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))

    val agentSession =
      AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(postcode)), registration = Some(registration), amlsDetails = Some(amlsSDetails))

    class ValidKnownFactsCached(val wasEligibleForMapping: Option[Boolean] = Some(false), includeInitialDetails: Boolean = true) {
      def persistedId: StashedChainnedSessionId = await(repo.create(ChainedSessionDetails(wasEligibleForMapping, agentSession)))
    }

    trait UnsubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, postcode)
    }

    trait SubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, postcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
    }

    trait PartiallySubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, postcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
    }

  }

  "context root" should {
    "redirect to start page" in {
      implicit val request = FakeRequest()
      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.StartController.start().url)
    }

    behave like anEndpointTakingContinueUrlAndRedirectingWithIt(controller.root(_))
  }

  "start" should {
    "not require authentication" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 200
    }

    "be available" in {
      val result = await(controller.start()(FakeRequest()))

      bodyOf(result) should include("Agent services account: sign in or set up")
    }

    "contain a start button pointing to /business-type" in {
      val result = await(controller.start(FakeRequest()))
      val doc = Jsoup.parse(bodyOf(result))
      val startLink = doc.getElementById("start")
      startLink.attr("href") shouldBe routes.BusinessTypeController.showBusinessTypeForm().url
      startLink.text() shouldBe htmlEscapedMessage("startpage.continue")
    }

    behave like aPageWithFeedbackLinks(request => controller.start(request))

    behave like aPageTakingContinueUrlAndContainingItAsALink(request => controller.start(request))
  }

  "showNotAgent" when {
    "the current user is logged in" should {

      "display the non-agent next steps page" in {
        implicit val request = authenticatedAs(individual)
        val result = await(controller.showNotAgent(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("nonAgent.title"))
      }

      "include link to create new account" in {
        val result = await(controller.showNotAgent(authenticatedAs(individual)))

        status(result) shouldBe 200
        bodyOf(result) should include("/redirect-to-sos")
      }
    }

    "the current user is not logged in" should {
      "redirect to the company-auth-frontend sign-in page" in {
        AuthStub.userIsNotAuthenticated()

        val request = FakeRequest()
        val result = await(controller.showNotAgent(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).head should include("gg/sign-in")
      }
    }

    behave like aPageWithFeedbackLinks(request => controller.showNotAgent(request), authenticatedAs(individual))
  }

  "returnAfterGGCredsCreated" should {
    import FixturesForReturnAfterGGCredsCreated._
    "redirect to the /check-answers page if given a valid StashedChainedSessionDetails ID" when {
      "agent is unsubscribed" in new ValidKnownFactsCached with UnsubscribedAgentStub {
        implicit val request = FakeRequest()
        sessionStoreService.currentSession.agentSession = Some(agentSession)

        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showCheckAnswers().url)
      }

      "agent is already fully subscribed" in new ValidKnownFactsCached with SubscribedAgentStub {
        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showCheckAnswers().url)
      }
    }

    "redirect to correct page if given a valid StashedChainedSessionDetails ID and agent is partially subscribed (subscribed in ETMP but not enrolled)" when {

      "agent was not eligible for mapping, should redirect to /subscription-complete" in new ValidKnownFactsCached(
        wasEligibleForMapping = Some(false),
        includeInitialDetails = false) with PartiallySubscribedAgentStub {
        AgentSubscriptionStub.partialSubscriptionWillSucceed(
          CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)),
          arn = "TARN00023")

        implicit val request = FakeRequest()
        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showSubscriptionComplete().url)
      }
    }

    "throw Upstream4xxResponse if agent-subscription returns 403 when completing partial subscription" in new ValidKnownFactsCached(
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)), 403)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream4xxResponse if agent-subscription returns 409 when completing partial subscription" in new ValidKnownFactsCached(
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)), 409)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream5xxResponse, 500 when executing partialSubscriptionFix" in new ValidKnownFactsCached(includeInitialDetails = false)
    with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)), 500)

      an[Upstream5xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "redirect to the /business-type page if given an invalid ChainedSessionDetails ID" in new ValidKnownFactsCached {
      val invalidId = s"A$persistedId"

      val result = await(controller.returnAfterGGCredsCreated(id = Some(invalidId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "redirect to /business-type page if there is no valid ChainedSessionDetails ID" in {
      val result = await(controller.returnAfterGGCredsCreated(id = None)(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "delete the persisted ChainedSessionDetails if given a valid ChainedSessionDetails ID" in new ValidKnownFactsCached with UnsubscribedAgentStub {
      val id = persistedId
      val result = await(controller.returnAfterGGCredsCreated(id = Some(id))(FakeRequest()))
      status(result) shouldBe 303
      await(repo.findChainedSessionDetails(id)) shouldBe None
    }

    "place the known facts back in the session store, if given a valid ChainedSessionDetails ID" in new ValidKnownFactsCached with UnsubscribedAgentStub {
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))
    }

    "place a provided continue URL in session store, if given a valid ChainedSessionDetails ID" in new ValidKnownFactsCached with UnsubscribedAgentStub {
      val continueUrl = ContinueUrl("/test-continue-url")
      implicit val request = FakeRequest(GET, s"?id=$persistedId&continue=${continueUrl.encodedUrl}")

      await(controller.returnAfterGGCredsCreated()(request))

      sessionStoreService.currentSession.continueUrl shouldBe Some(continueUrl)
    }
  }
}

class StartControllerWithAutoMappingOn extends StartControllerISpec {
  import FixturesForReturnAfterGGCredsCreated._
  override def featureFlagAutoMapping: Boolean = true

  "returnAfterGGCredsCreated" should {
    import FixturesForReturnAfterGGCredsCreated.{PartiallySubscribedAgentStub, UnsubscribedAgentStub, ValidKnownFactsCached}

    "given normal flow" when {
      "not session store wasEligibleForMapping as flag is false" in new ValidKnownFactsCached(wasEligibleForMapping = None) {
        implicit val request = FakeRequest()

        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))
        sessionStoreService.currentSession.wasEligibleForMapping shouldBe Some(false)
      }
    }

    "agent was eligible for mapping, should redirect to /link-clients" in new ValidKnownFactsCached(
      wasEligibleForMapping = Some(true),
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub.partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)))

      implicit val request = FakeRequest()
      sessionStoreService.currentSession.agentSession = Some(agentSession)
      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.SubscriptionController.showLinkClients().url)
      result.session.get("isPartiallySubscribed").contains("true") shouldBe true
    }

    "agent NOT Eligible for mapping, should redirect to /link-clients" in new ValidKnownFactsCached(
      wasEligibleForMapping = Some(false),
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub.partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(postcode)))

      implicit val request = FakeRequest()
      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.SubscriptionController.showSubscriptionComplete().url)
    }

    "place the mapping eligibility back in session store, if given a valid ChainedSessionDetails ID" in new ValidKnownFactsCached with UnsubscribedAgentStub {
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      sessionStoreService.currentSession.wasEligibleForMapping shouldBe wasEligibleForMapping
    }
  }
}

class StartControllerWithAutoMappingOff extends StartControllerISpec {
  override def featureFlagAutoMapping: Boolean = false

  "returnAfterGGCredsCreated" should {
    import FixturesForReturnAfterGGCredsCreated._

    "do not store wasEligibleForMapping in sessionStore" in new ValidKnownFactsCached(wasEligibleForMapping = None) {
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))
      sessionStoreService.currentSession.wasEligibleForMapping shouldBe None
    }

    "do not store wasEligibleForMapping in sessionStore even if wasEligibleForMapping exists" in new ValidKnownFactsCached(wasEligibleForMapping = Some(true)) {
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))
      sessionStoreService.currentSession.wasEligibleForMapping shouldBe None
    }
  }
}
