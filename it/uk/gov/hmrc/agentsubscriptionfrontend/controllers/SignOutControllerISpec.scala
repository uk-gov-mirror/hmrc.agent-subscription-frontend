package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.FakeRequest
import play.api.test.Helpers.redirectLocation
import play.api.test.Helpers._

class SignOutControllerISpec extends BaseControllerISpec {
  private lazy val controller: SignedOutController = app.injector.instanceOf[SignedOutController]

  private lazy val logoutRedirectUrl = "/government-gateway-registration-frontend/choose-your-account"

  private val fakeRequest = FakeRequest()

  "context signed out" should {
    "redirect to start page" in {
      val result = await(controller.signOut(fakeRequest))

      status(result) shouldBe 303
      redirectLocation(result).head should include(logoutRedirectUrl)
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

