package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import org.jsoup.Jsoup
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType, Postcode}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.agentSession

import scala.concurrent.ExecutionContext.Implicits.global

class PostcodeControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "showPostcodePage" should {

    "display the page with correct content" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(BusinessType.SoleTrader))))

      val result = await(controller.showPostcodeForm()(request))

      result should containMessages(
        "postcode.title"
      )
    }

    "redirect to /business-type if businessType is not found in session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "pre-populate the postcode if one is already stored in the session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), Some(Utr("abcd")), postcode = Some(Postcode("AB12 3EF")))))

      val result = await(controller.showPostcodeForm()(request))

      val doc = Jsoup.parse(bodyOf(result))
      val link = doc.getElementById("postcode")
      link.attr("value") shouldBe "AB12 3EF"
    }
  }

  "submitPostcodeForm" should {

    "read the form and redirect to /national-insurance-number if businessType is SoleTrader or Partnership" in {
      List(SoleTrader, Partnership).foreach { businessType =>
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = Some(Postcode("AA12 1JN")), nino = None))
      }
    }

    "read the form and redirect to /company-registration-number if businessType is Limited Company or Llp" in {
      List(LimitedCompany, Llp).foreach { businessType =>
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = Some(Postcode("AA12 1JN")), nino = None))
      }
    }

    "redirect to /business-type if businessType is not found in session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "AA12 1JN")

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "handle for with invalid postcodes" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> "sdsds")
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader))))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 200

      result should containMessages(
        "postcode.title",
        "error.postcode.invalid"
      )
    }
  }
}
