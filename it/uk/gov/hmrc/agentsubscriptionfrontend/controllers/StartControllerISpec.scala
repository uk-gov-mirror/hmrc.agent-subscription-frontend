package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.ChainedSessionDetailsRepository
import uk.gov.hmrc.agentsubscriptionfrontend.repository.StashedChainedSessionDetails.StashedChainnedSessionId
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{individual, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

trait StartControllerISpec extends BaseISpec {

  protected lazy val controller: StartController = app.injector.instanceOf[StartController]
  protected lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"
  protected lazy val repo = app.injector.instanceOf[ChainedSessionDetailsRepository]

  private val id = AuthProviderId("12345-credId")
  private val continueId = ContinueId("foobar")
  private val record = TestData.minimalSubscriptionJourneyRecord(id)

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder

  object FixturesForReturnAfterGGCredsCreated {

    val amlsSDetails = AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))

    val agentSession =
      AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode(testPostcode)), registration = Some(testRegistration), amlsDetails = Some(amlsSDetails))

    class ValidKnownFactsCached(val wasEligibleForMapping: Option[Boolean] = Some(false), includeInitialDetails: Boolean = true) {
      def persistedId: StashedChainnedSessionId = await(repo.create(ChainedSessionDetails(agentSession)))
    }

    trait UnsubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode)
    }

    trait SubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
    }

    trait PartiallySubscribedAgentStub {
      self: ValidKnownFactsCached =>
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
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

  trait SetupUnsubscribed {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
      subscribingAgentEnrolledForNonMTD)
    givenNoSubscriptionJourneyRecordExists(id)
    givenSubscriptionJourneyRecordExists(continueId, record.copy(continueId = Some(continueId.value)))
    givenSubscriptionRecordCreated(record.authProviderId, record.copy(continueId = Some(continueId.value)))
  }

  "returnAfterGGCredsCreated" should {

    import FixturesForReturnAfterGGCredsCreated._

    "given a valid subscription journey record" when {

      "redirect to the /task-list page and update journey record with new clean creds id" in new SetupUnsubscribed {
        implicit val request = FakeRequest()

        val result = await(controller.returnAfterGGCredsCreated(id = Some(continueId.value))(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.TaskListController.showTaskList().url)
      }

      "redirect to the /task-list page when there is no continueId" in {
        implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
          subscribingAgentEnrolledForNonMTD)
        givenNoSubscriptionJourneyRecordExists(id)
        implicit val request = FakeRequest()

        val result = await(controller.returnAfterGGCredsCreated()(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
      }
    }

    // TODO: move partial subscription test when decision made on where to complete partial subscription
    "redirect to correct page if given a valid StashedChainedSessionDetails ID and agent is partially subscribed (subscribed in ETMP but not enrolled)" when {

      "agent was not eligible for mapping, should redirect to /subscription-complete" ignore new ValidKnownFactsCached(
        wasEligibleForMapping = Some(false),
        includeInitialDetails = false) with PartiallySubscribedAgentStub {
        AgentSubscriptionStub.partialSubscriptionWillSucceed(
          CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(testPostcode)),
          arn = "TARN00023")

        implicit val request = FakeRequest()
        val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

        status(result) shouldBe 303
        redirectLocation(result).head should include(routes.SubscriptionController.showSubscriptionComplete().url)
      }
    }

    "throw Upstream4xxResponse if agent-subscription returns 403 when completing partial subscription" ignore new ValidKnownFactsCached(
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(testPostcode)), 403)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream4xxResponse if agent-subscription returns 409 when completing partial subscription" ignore new ValidKnownFactsCached(
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(testPostcode)), 409)

      an[Upstream4xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }

    "throw Upstream5xxResponse, 500 when executing partialSubscriptionFix" ignore new ValidKnownFactsCached(includeInitialDetails = false)
    with PartiallySubscribedAgentStub {
      AgentSubscriptionStub
        .partialSubscriptionWillReturnStatus(CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(testPostcode)), 500)

      an[Upstream5xxResponse] shouldBe thrownBy(await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest())))
    }
  }
}

class StartControllerTests extends StartControllerISpec {

  "returnAfterGGCredsCreated" should {
    import FixturesForReturnAfterGGCredsCreated.{PartiallySubscribedAgentStub, ValidKnownFactsCached}

    "agent NOT Eligible for mapping, should redirect to /link-clients" ignore new ValidKnownFactsCached(
      wasEligibleForMapping = Some(false),
      includeInitialDetails = false) with PartiallySubscribedAgentStub {
      AgentSubscriptionStub.partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(utr = validUtr, knownFacts = SubscriptionRequestKnownFacts(testPostcode)))

      implicit val request = FakeRequest()
      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include(routes.SubscriptionController.showSubscriptionComplete().url)
    }
  }
}
