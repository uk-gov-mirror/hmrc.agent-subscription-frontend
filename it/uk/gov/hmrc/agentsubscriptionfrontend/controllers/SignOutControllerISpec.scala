package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.net.URLEncoder
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global


class SignOutControllerISpec extends BaseISpec {
  private lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]

  private lazy val sosRedirectUrl = "/government-gateway-registration-frontend?accountType=agent"

  private lazy val repo = app.injector.instanceOf[KnownFactsResultMongoRepository]

  private val fakeRequest = FakeRequest()

  "redirect to SOS" should {
    "redirect to SOS page" in {
      val result = await(controller.redirectToSos(authenticatedRequest()))

      status(result) shouldBe 303
      redirectLocation(result).head should include(sosRedirectUrl)
    }

    "save the KnownFactsResults in the DB" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)

      await(repo.find("knownFactsResult.utr" -> "9876543210").map(_.headOption.map(_.knownFactsResult))) shouldBe None
      await(controller.redirectToSos(request))
      await(repo.find("knownFactsResult.utr" -> "9876543210").map(_.headOption.map(_.knownFactsResult))) shouldBe Some(knownFactsResult)
    }

    "include an ID of the saved KnownFactsResults in the SOS redirect URL" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)

      val result = await(controller.redirectToSos(request))
      val id = await(repo.find("knownFactsResult.utr" -> "9876543210").map(_.headOption.map(_.id))).get
      redirectLocation(result).head should include (s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3D$id")
    }

    "not include an ID in the SOS redirect URL when KnownFactsResults are not yet known" in {
      implicit val request = authenticatedRequest()

      val result = await(controller.redirectToSos(request))
      redirectLocation(result).head should include (s"continue=%2Fagent-subscription%2Freturn-after-gg-creds-created")
    }

    "include a continue URL in the SOS redirect URL if a continue URL exists in the session store" in {
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession(hc(request)).continueUrl = Some(ourContinueUrl)

      val result = await(controller.redirectToSos(authenticatedRequest()))

      val sosContinueValueUnencoded = s"/agent-subscription/return-after-gg-creds-created?continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=${sosContinueValueEncoded}"
      redirectLocation(result).head should include (expectedSosContinueParam)
    }

    "include both an ID and a continue URL in the SOS redirect URL if both a continue URL and KnownFacts exist in the session store" in {
      val knownFactsResult = KnownFactsResult(Utr("9876543210"), "AA11AA", "Test organisation name", isSubscribedToAgentServices = true)
      val ourContinueUrl = ContinueUrl("/test-continue-url")
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(knownFactsResult)
      sessionStoreService.currentSession.continueUrl = Some(ourContinueUrl)

      val result = await(controller.redirectToSos(request))
      val id = await(repo.find("knownFactsResult.utr" -> "9876543210").map(_.headOption.map(_.id))).get

      val sosContinueValueUnencoded = s"/agent-subscription/return-after-gg-creds-created?id=$id&continue=${ourContinueUrl.encodedUrl}"
      val sosContinueValueEncoded = URLEncoder.encode(sosContinueValueUnencoded, "UTF-8")
      val expectedSosContinueParam = s"continue=${sosContinueValueEncoded}"
      redirectLocation(result).head should include (expectedSosContinueParam)
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
}

