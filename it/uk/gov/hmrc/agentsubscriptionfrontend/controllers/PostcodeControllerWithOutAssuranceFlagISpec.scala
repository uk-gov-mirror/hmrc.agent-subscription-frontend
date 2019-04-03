package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, CompletePartialSubscriptionBody, SubscriptionRequestKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class PostcodeControllerWithOutAssuranceFlagISpec extends BaseISpec with SessionDataMissingSpec {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> false,
        "features.agent-assurance-paye-check" -> true,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: PostcodeController = app.injector.instanceOf[PostcodeController]

  "submitPostcodeForm" should {

    "read the form and redirect to /national-insurance-number if businessType is SoleTrader or Partnership" in {
      List(SoleTrader, Partnership).foreach { businessType =>
        withMatchingUtrAndPostcode(validUtr, validPostcode)

        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.NationalInsuranceController.showNationalInsuranceNumberForm().url)

        sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(registration.copy(emailAddress = Some("someone@example.com")))
      }
    }

    "read the form and redirect to /company-registration-number if businessType is Limited Company or Llp" in {
      List(LimitedCompany, Llp).foreach { businessType =>
        withMatchingUtrAndPostcode(validUtr, validPostcode)

        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD).withFormUrlEncodedBody("postcode" -> validPostcode)
        sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(businessType), postcode = None, nino = None))

        val result = await(controller.submitPostcodeForm()(request))

        status(result) shouldBe 303

        redirectLocation(result) shouldBe Some(routes.CompanyRegistrationController.showCompanyRegNumberForm().url)

        sessionStoreService.currentSession.agentSession.get.registration shouldBe Some(registration.copy(emailAddress = Some("someone@example.com")))
      }
    }

    "redirect to already subscribed page when the business registration found by agent-subscription is already subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession.copy(businessType = Some(SoleTrader), postcode = None, nino = None))

      val result = await(controller.submitPostcodeForm()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
    }

    "showSubscriptionComplete for partially subscribed agent" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = false, isSubscribedToETMP = true)
      partialSubscriptionWillSucceed(CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)), arn = "TARN00023")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("postcode" -> validPostcode)
      sessionStoreService.currentSession.agentSession = Some(agentSession)

      val result = await(controller.submitPostcodeForm()(request))
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showSubscriptionComplete().url)
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