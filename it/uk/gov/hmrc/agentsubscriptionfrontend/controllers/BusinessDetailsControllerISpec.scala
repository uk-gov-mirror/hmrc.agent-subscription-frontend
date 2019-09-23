package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessAddress, Postcode, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

class BusinessDetailsControllerISpec extends BaseISpec {

  lazy val controller: BusinessDetailsController = app.injector.instanceOf[BusinessDetailsController]

  "GET /business-details" should {
    "display the business details page" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(controller.showBusinessDetailsForm(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "Enter your business details",
        "Your Self Assessment Unique Taxpayer Reference (UTR)",
        "Registered business postcode")
    }
  }

  "POST /business-details" should {

    "redirect to existing journey found page if journey found by UTR" in new TestSetupWithCompleteJourneyRecord {
      AgentSubscriptionJourneyStub.givenSubscriptionJourneyRecordExists(utr, minimalSubscriptionJourneyRecord(id))
      withMatchingUtrAndPostcode(utr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = false)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showExistingJourneyFound().url)
    }

    "redirect to confirm business and update session with new business details" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(utr, validPostcode)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      sessionStoreService.currentSession.agentSession shouldBe Some(
        AgentSession(
          Some(SoleTrader),
          Some(utr),
          Some(Postcode(validPostcode)),
          registration = Some(Registration(
            Some("My Agency"),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = false,
            BusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some("AA11AA"),
              "GB"),
            Some("someone@example.com")
          ))
        ))
    }

    "redirect to confirm business and update session with new business details when user is partially subscribed" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(utr, validPostcode)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      sessionStoreService.currentSession.agentSession shouldBe Some(
        AgentSession(
          Some(SoleTrader),
          Some(utr),
          Some(Postcode(validPostcode)),
          registration = Some(Registration(
            Some("My Agency"),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = false,
            BusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some("AA11AA"),
              "GB"),
            Some("someone@example.com")
          ))
        ))
    }

    "redisplay the form with errors if the utr is invalid" in new TestSetupNoJourneyRecord {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(request.withFormUrlEncodedBody("utr" -> "foo", "postcode" -> validPostcode)))
      status(result) shouldBe 200
      checkHtmlResultWithBodyText(
        result,
        "There is a problem",
        "The Unique Taxpayer Reference (UTR) you use for Self Assessment must be 10 numbers")
    }

    "redirect to already subscribed when the user is already subscribed" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(utr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
    }

    "redirect to confirm business when the user is partially subscribed" in new TestSetupNoJourneyRecord {
      withMatchingUtrAndPostcode(utr, validPostcode, isSubscribedToETMP = true)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
    }

    "redirect to no match found when there subscription status is not valid" in new TestSetupNoJourneyRecord {
      withNonMatchingUtrAndPostcode(utr, validPostcode)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result: Result = await(
        controller.submitBusinessDetails(
          request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoMatchFound().url)
    }
  }
}
