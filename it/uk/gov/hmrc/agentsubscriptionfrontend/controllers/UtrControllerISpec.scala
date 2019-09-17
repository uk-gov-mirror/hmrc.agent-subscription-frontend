package uk.gov.hmrc.agentsubscriptionfrontend.controllers
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{validBusinessTypes, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

class UtrControllerISpec extends BaseISpec with SessionDataMissingSpec {

  lazy val controller: UtrController = app.injector.instanceOf[UtrController]

  "showUtrFormPage" should {

    validBusinessTypes.foreach { businessType =>

      s"display the page as expected when is business type is $businessType" in new TestSetupNoJourneyRecord {

        implicit val request: FakeRequest[AnyContentAsEmpty.type] =
          authenticatedAs(subscribingAgentEnrolledForNonMTD)
        await(sessionStoreService.cacheAgentSession(AgentSession(Some(businessType))))

        private val result = await(controller.showUtrForm()(request))

        result should containMessages(
          "utr.title",
          s"utr.header.${businessType.key}"
        )

        result should containSubstrings(Messages(s"utr.description.about.${businessType.key}"))
      }
    }

    "pre-populate the utr if one is already stored in the session" in  new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD)
      await(sessionStoreService.cacheAgentSession(AgentSession(Some(SoleTrader), Some(Utr("abcd")))))

      private val result = await(controller.showUtrForm()(request))

      result should containInputElement("utr", "text", Some("abcd"))
    }
  }

  "submitUtrFormPage" should {

    "display the page as expected when the form is valid and redirect to /postcode page" in  new TestSetupNoJourneyRecord{

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("utr" -> validUtr.value)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader)))

      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.PostcodeController.showPostcodeForm().url)
    }

    "redirect to /business-type if businessType missing in the session" in  new TestSetupNoJourneyRecord {

      private val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("utr" -> validUtr.value)
      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
    }

    "handle form with errors and show the same again" in  new TestSetupNoJourneyRecord {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("utr" -> "invalidUtr")
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(BusinessType.SoleTrader)))

      private val result = await(controller.submitUtrForm()(request))

      status(result) shouldBe 200
      result should containMessages(
        "utr.title",
        s"utr.header.${BusinessType.SoleTrader.key}",
        "error.sautr.invalid"
      )
    }
  }
}
