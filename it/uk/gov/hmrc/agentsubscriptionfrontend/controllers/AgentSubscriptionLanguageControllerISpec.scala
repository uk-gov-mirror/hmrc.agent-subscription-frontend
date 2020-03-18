package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import play.api.test.Helpers.{cookies, redirectLocation}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingCleanAgentWithoutEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.id

import scala.concurrent.duration._


class AgentSubscriptionLanguageControllerISpec extends BaseISpec {

  lazy private val controller: AgentSubscriptionLanguageController = app.injector.instanceOf[AgentSubscriptionLanguageController]

  implicit val timeout = 2.seconds

  val utr = Utr("2000000000")
  trait Setup {
    implicit val authenticatedRequest: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(
      subscribingCleanAgentWithoutEnrolments)
    givenAgentIsNotManuallyAssured(utr.value)
    givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecord(id))
  }

  "GET /language/:lang" should {

    val request = FakeRequest("GET", "/language/english")

    "redirect to https://www.tax.service.co.uk/agent-subscription/start when the request header contains no referer" in {

      val result = controller.switchToLanguage("english")(request)
      status(result) shouldBe 303
      redirectLocation(result)(timeout) shouldBe Some("https://www.tax.service.gov.uk/agent-subscription/start")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "en"
    }

    "redirect to /some-page when the request header contains referer /some-page" in {

      val request = FakeRequest("GET", "/language/english").withHeaders("referer" -> "/some-page")

      val result = controller.switchToLanguage("english")(request)
      status(result) shouldBe 303
      redirectLocation(result)(timeout) shouldBe Some("/some-page")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "en"

    }

    "redirect to /check-money-laundering-compliance with welsh language when toggle pressed on that page" in {

      val request = FakeRequest("GET", "/language/english").withHeaders("referer" -> "/check-money-laundering-compliance")

      val result = await(controller.switchToLanguage("cymraeg")(request))
      status(result) shouldBe 303
      redirectLocation(result)(timeout) shouldBe Some("/check-money-laundering-compliance")

      cookies(result)(timeout).get("PLAY_LANG").get.value shouldBe "cy"
    }
  }
}
