package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentAssuranceConnectorISpec extends BaseISpec with MetricTestSupport {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private lazy val connector =
    new AgentAssuranceConnector(
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Metrics],
      app.injector.instanceOf[AppConfig]
      )

  "getRegistration PAYE" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-PAYE")(connector.hasAcceptableNumberOfPayeClients)
  }

  "getRegistration SA" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-SA")(connector.hasAcceptableNumberOfSAClients)
  }

  "getRegistration HMCE-VATDEC-ORG" should {
    behave like testAcceptableNumberOfClientsEndpoint("HMCE-VATDEC-ORG")(
      connector.hasAcceptableNumberOfVatDecOrgClients)
  }

  "getRegistration IR-CT" should {
    behave like testAcceptableNumberOfClientsEndpoint("IR-CT")(
      connector.hasAcceptableNumberOfIRCTClients)
  }

  "hasActiveCesaRelationship" should {
    "receive 200 if valid combination passed and relationship exists in Cesa Nino" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")
        await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe true
      }
    }

    "receive 200 if valid combination passed and relationship exists in Cesa Utr" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")
        await(connector.hasActiveCesaRelationship(Utr("4000000009"), "utr", SaAgentReference("SA6012"))) shouldBe true
      }
    }

    "receive 403 if valid combination passed and relationship does not exist in Cesa" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")
        await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe false
      }
    }

    "receive 403 if invalid combination passed" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenABadCombinationAndUserHasRelationshipInCesa("nino", "AB123456A", "SA126013")
        await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      }
    }

    "receive 404 when valid Nino but is not found in DB" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET") {
        givenAGoodCombinationAndNinoNotFoundInCesa("nino", "AB123456B", "SA126012")
        await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      }
    }
  }

  "getR2DWAgents" should {
    val utr = Utr("2000000009")
    "return true is utr found in r2dw list" in {
      givenRefusalToDealWithUtrIsForbidden(utr.value)
      await(connector.isR2DWAgent(utr)) shouldBe true
    }
    "return false is utr not found in r2dw list" in {
      givenRefusalToDealWithUtrIsNotForbidden(utr.value)
      await(connector.isR2DWAgent(utr)) shouldBe false
    }
    "return false is r2dw list is empty" in {
      givenRefusalToDealWithUtrIsNotForbidden(utr.value)
      await(connector.isR2DWAgent(utr)) shouldBe false
    }
    "return illegal state exception when " in {
      val utr1 = Utr("1234567")
      givenRefusalToDealWithReturns404(utr1.value)
      intercept[IllegalStateException] {
        await(connector.isR2DWAgent(utr1))
      }
    }

  }

  "getManuallyAssuredAgents" should {
    val utr = Utr("2000000009")
    "return true is utr found in the manually assured agents list" in {
      givenAgentIsManuallyAssured(utr.value)
      await(connector.isManuallyAssuredAgent(utr)) shouldBe true
    }
    "return false if utr not found in the manually assured agents list" in {
      givenAgentIsNotManuallyAssured(utr.value)
      await(connector.isManuallyAssuredAgent(utr)) shouldBe false
    }
    "return false if the manually assured agents list is empty" in {
      givenAgentIsNotManuallyAssured(utr.value)
      await(connector.isManuallyAssuredAgent(utr)) shouldBe false
    }
    "throw illegal state exception when agent-assurance responds with 404" in {
      givenManuallyAssuredAgentsReturns(utr.value, 404)
      intercept[IllegalStateException] {
        await(connector.isManuallyAssuredAgent(utr))
      }
    }
    "throw Upstream4xxResponse when agent-assurance responds with 401" in {
      givenManuallyAssuredAgentsReturns(utr.value, 401)
      intercept[UpstreamErrorResponse] {
        await(connector.isManuallyAssuredAgent(utr))
      }
    }
    "throw Upstream5xxResponse when agent-assurance responds with 500" in {
      givenManuallyAssuredAgentsReturns(utr.value, 500)
      intercept[UpstreamErrorResponse] {
        await(connector.isManuallyAssuredAgent(utr))
      }
    }
    "monitor with metric ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET" in {
      withMetricsTimerUpdate("ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET") {
        givenAgentIsManuallyAssured(utr.value)

        await(connector.isManuallyAssuredAgent(utr))
      }
    }
  }



  def testAcceptableNumberOfClientsEndpoint(service: String)(method: => Future[Boolean]) = {
    s"return true when the current logged in user has an acceptable number of $service clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfClients(service)
      await(method) shouldBe true
    }

    s"return false when the current logged in user does not have an acceptable number of $service clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfClients(service)
      await(method) shouldBe false
    }

    s"return false when the current user is not authenticated for $service" in {
      givenUserIsNotAuthenticatedForClientCheck(service)
      await(method) shouldBe false
    }

    s"throw an exception when appropriate for $service" in {
      givenAnExceptionOccursDuringTheClientCheck(service)
      intercept[Exception] {
        await(method)
      }
    }
  }
}
