package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import uk.gov.hmrc.agentsubscriptionfrontend.models.AgentSession
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD

class BusinessDetailsControllerISpec extends BaseISpec {

  lazy val controller: BusinessDetailsController = app.injector.instanceOf[BusinessDetailsController]

  "GET /business-details" should {
    "display the business details page" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result = await(controller.showBusinessDetailsForm(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "Enter your business details",
        "Your Self Assessment Unique Taxpayer Reference (UTR)",
        "Registered business postcode")
    }
  }

}
