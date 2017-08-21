package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerISpec extends BaseISpec {

  private lazy val controller: StartController = app.injector.instanceOf[StartController]
  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"
  private lazy val repo = app.injector.instanceOf[KnownFactsResultMongoRepository]

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("government-gateway.url" -> configuredGovernmentGatewayUrl)

  "context root" should {
    "redirect to start page" in {
      implicit val request = FakeRequest()
      val result = await(controller.root(request))

      status(result) shouldBe 303
      redirectLocation(result).head should include("/start")
    }

    "include an absolute continue URL in the redirect" in {
      val url = "http://localhost"
      val result = await(controller.root(FakeRequest("GET", s"/?continue=${URLEncoder.encode(url, "UTF-8")}")))

      status(result) shouldBe 303
      redirectLocation(result).head should include(s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")
    }

    "not include a continue URL if it's invalid" in {
      val result = await(controller.root(FakeRequest("GET", "/?continue=http://foo@bar:1234")))

      status(result) shouldBe 303
      redirectLocation(result).head should not include("continue=")
    }

    "not include a continue URL if it's not provided" in {
      val result = await(controller.root(FakeRequest("GET", "/")))

      status(result) shouldBe 303
      redirectLocation(result).head should not include("continue=")
    }
  }

  "start" should {
    "not require authentication" in {
      AuthStub.userIsNotAuthenticated()

      val result = await(controller.start(FakeRequest()))

      status(result) shouldBe 200
    }

    "be available" in {
      val result = await(controller.start()(FakeRequest()))

      bodyOf(result) should include("Create your Agent Services account")
    }

    behave like aPageWithFeedbackLinks(request => controller.start(request))

    "start redirects" should {
      "include absolute continue URL" in {
        val url = "http://localhost"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should include(s"continue=${URLEncoder.encode(url, "UTF-8")}")
      }

      "include relative continue URL" in {
        val url = "/foo"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should include(s"continue=${URLEncoder.encode(url, "UTF-8")}")
      }

      "include continue URL if it's the absolute www.tax.service.gov.uk continue url" in {
        val url = "http://www.tax.service.gov.uk/foo/bar?some=true"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should include(s"continue=${URLEncoder.encode(url, "UTF-8")}")
      }

      "include continue URL if it's whitelisted" in {
        val url = "http://www.foo.com/bar?some=false"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should include(s"continue=${URLEncoder.encode(url, "UTF-8")}")
      }

      "not include a continue URL if it contains an invalid character" in {
        val url = "http://www@foo.com"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should not include("continue=")
      }

      "not include a continue URL if it's not whitelisted" in {
        val url = "http://www.foo.org/bar?some=false"
        val result = await(controller.start()(FakeRequest("GET", s"/start?continue=${URLEncoder.encode(url, "UTF-8")}")))

        status(result) shouldBe 200
        bodyOf(result) should not include("continue=")
      }

      "not include a continue URL if it's not provided" in {
        val result = await(controller.start()(FakeRequest("GET", "/start")))

        status(result) shouldBe 200
        bodyOf(result) should not include("continue=")
      }
    }
  }

  "showNonAgentNextSteps" when {
    "the current user is logged in" should {

      "display the non-agent next steps page"  in {
        implicit val request = authenticatedRequest()
        val result = await(controller.showNonAgentNextSteps(request))

        status(result) shouldBe OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
        bodyOf(result) should include(htmlEscapedMessage("nonAgent.title"))
      }

      "include link to create new account" in {
        val result = await(controller.showNonAgentNextSteps(authenticatedRequest()))

        status(result) shouldBe 200
        bodyOf(result) should include("/redirect-to-sos")
      }
    }

    "the current user is not logged in" should {
      "redirect to the company-auth-frontend sign-in page" in {
        AuthStub.userIsNotAuthenticated()

        val request = FakeRequest()
        val result = await(controller.showNonAgentNextSteps(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).head should include("gg/sign-in")
      }
    }

    behave like aPageWithFeedbackLinks(request => controller.showNonAgentNextSteps(request), authenticatedRequest())
  }

  "returnAfterGGCredsCreated" should {
    "redirect to the subscription-details page if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))

      val result = await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/subscription-details")
    }

    "redirect to the check-agency-status page if given an invalid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      val invalidId = s"A$persistedId"

      val result = await(controller.returnAfterGGCredsCreated(id = Some(invalidId))(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/check-agency-status")
    }

    "redirect to check-agency-status page if there is no valid KnownFactsResult ID" in {
      val result = await(controller.returnAfterGGCredsCreated(id = None)(FakeRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include ("/check-agency-status")
    }

    "delete the persisted KnownFactsResult if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(FakeRequest()))

      await(repo.findKnownFactsResult(persistedId)) shouldBe None
    }

    "repopulate the KnownFacts session store with the persisted KnownFactsResult, if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      implicit val request = FakeRequest()

      await(controller.returnAfterGGCredsCreated(id = Some(persistedId))(request))

      sessionStoreService.currentSession.knownFactsResult shouldBe Some(knownFactsResult)
    }

    "place a provided continue URL in session store, if given a valid KnownFactsResult ID" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val persistedId = await(repo.create(knownFactsResult))
      val continueUrl = ContinueUrl("/test-continue-url")
      implicit val request = FakeRequest(GET, s"?id=$persistedId&continue=${continueUrl.encodedUrl}")

      await(controller.returnAfterGGCredsCreated()(request))

      sessionStoreService.currentSession.continueUrl shouldBe Some(continueUrl)
    }
  }
}
