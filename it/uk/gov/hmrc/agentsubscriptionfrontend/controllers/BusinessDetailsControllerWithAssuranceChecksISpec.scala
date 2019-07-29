package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.google.inject.AbstractModule
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.SsoConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessAddress, Postcode, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

class BusinessDetailsControllerWithAssuranceChecksISpec extends BaseISpec {

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[SessionStoreService]).toInstance(sessionStoreService)
      bind(classOf[SsoConnector]).toInstance(testSsoConnector)
    }
  }

  override implicit lazy val app: Application = appBuilder.build()

  override protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                    -> wireMockPort,
        "microservice.services.agent-subscription.port"      -> wireMockPort,
        "microservice.services.address-lookup-frontend.port" -> wireMockPort,
        "microservice.services.sso.port"                     -> wireMockPort,
        "microservice.services.agent-assurance.port"         -> wireMockPort,
        "microservice.services.agent-mapping.port"           -> wireMockPort,
        "auditing.enabled"                                   -> true,
        "auditing.consumer.baseUri.host"                     -> wireMockHost,
        "auditing.consumer.baseUri.port"                     -> wireMockPort,
        "features.agent-assurance-run"                       -> true
      )
      .overrides(new TestGuiceModule)

  lazy val controller: BusinessDetailsController = app.injector.instanceOf[BusinessDetailsController]

  "POST /business-details" should {
    "redirect to cannot create account when user is on the R2DW list" in new TestSetupNoJourneyRecord {
      givenRefusalToDealWithUtrIsForbidden(utr.value)
      withMatchingUtrAndPostcode(utr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result = await(controller.submitBusinessDetails(
        request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
    }

    "redirect to start of invasive checks when user fails the invisible assurance checks" in new TestSetupNoJourneyRecord {
      givenRefusalToDealWithUtrIsNotForbidden(utr.value)
      givenAgentIsNotManuallyAssured(utr.value)
      withMatchingUtrAndPostcode(utr, validPostcode)
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-PAYE")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-SA")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("HMCE-VATDEC-ORG")
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients("IR-CT")

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result = await(controller.submitBusinessDetails(
        request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AssuranceChecksController.invasiveCheckStart().url)
    }

    "redirect to confirm business when user is manually assured" in new TestSetupNoJourneyRecord {
      givenRefusalToDealWithUtrIsNotForbidden(utr.value)
      givenAgentIsManuallyAssured(utr.value)
      withMatchingUtrAndPostcode(utr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.agentSession = Some(AgentSession(Some(SoleTrader)))

      val result = await(controller.submitBusinessDetails(
        request.withFormUrlEncodedBody("utr" -> utr.value, "postcode" -> validPostcode)))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)
    }
  }
}
