package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import org.jsoup.Jsoup
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionJourneyStub, AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.{individual, subscribingAgentEnrolledForNonMTD}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}

class StartControllerISpec extends BaseISpec {

  protected lazy val controller: StartController = app.injector.instanceOf[StartController]
  protected lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  private val id = AuthProviderId("12345-credId")
  private val continueId = ContinueId("foobar")
  private val record = TestData.minimalSubscriptionJourneyRecord(id)

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder

  object FixturesForReturnAfterGGCredsCreated {

    val amlsSDetails = AmlsDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))

    val agentSession =
      AgentSession(
        Some(BusinessType.SoleTrader),
        utr = Some(validUtr),
        postcode = Some(Postcode(testPostcode)),
        registration = Some(testRegistration))

    trait UnsubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, testPostcode)
    }

    trait SubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        validUtr,
        testPostcode,
        isSubscribedToAgentServices = true,
        isSubscribedToETMP = true)
    }

    trait PartiallySubscribedAgentStub {
      AgentSubscriptionStub.withMatchingUtrAndPostcode(
        validUtr,
        testPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionJourneyStub.givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
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
        implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD)
        givenNoSubscriptionJourneyRecordExists(id)
        implicit val request = FakeRequest()

        val result = await(controller.returnAfterGGCredsCreated()(request))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
      }
    }

    "returnAfterMapping" should {

      "given a valid subscription journey record" when {

        "redirect to the /task-list page and update journey record with mappingComplete as true" in {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          givenSubscriptionJourneyRecordExists(id, record)
          givenSubscriptionRecordCreated(record.authProviderId, record.copy(continueId = None, mappingComplete = true))

          val result = await(controller.returnAfterMapping()(request))

          status(result) shouldBe 303
          redirectLocation(result).head should include(routes.TaskListController.showTaskList().url)
        }

        "throw a runtime exception when there is no record" in {
          implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] =
            authenticatedAs(subscribingAgentEnrolledForNonMTD)
          givenNoSubscriptionJourneyRecordExists(id)
          intercept[RuntimeException] {
            await(controller.returnAfterMapping()(authenticatedRequest))
          }.getMessage shouldBe "Expected Journey Record missing"
        }
      }
    }
  }

  "GET /cannot-create-account" should {
    "display the cannot create account page" in {
      implicit val request = FakeRequest()

      val result = await(controller.showCannotCreateAccount()(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(
        result,
        "We could not confirm your identity",
        "Before you can create an agent services account, we need to be sure that a client has authorised you to deal with HMRC."
      )
    }
  }
}
