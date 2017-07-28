package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global

class SignOutControllerISpec extends BaseControllerISpec {
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
      redirectLocation(result).head should include (s"continue=http%3A%2F%2F%2Fagent-subscription%2Freturn-after-gg-creds-created%3Fid%3D$id")
    }

    "not include an ID in the SOS redirect URL when KnownFactsResults are not yet known" in {
      implicit val request = authenticatedRequest()

      val result = await(controller.redirectToSos(request))
      redirectLocation(result).head should include (s"continue=http%3A%2F%2F%2Fagent-subscription%2Freturn-after-gg-creds-created")
    }
  }

  "start survey" should {
    "redirect to the survey page" in {
      val result = await(controller.startSurvey(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include("feedback-survey")
    }
  }
}

