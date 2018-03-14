package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.HttpVerbs
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import com.kenshoo.play.metrics.Metrics

class AgentAssuranceConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with MetricTestSupport{

  private implicit val hc = HeaderCarrier()

  private lazy val connector = new AgentAssuranceConnector(new URL(s"http://localhost:$wireMockPort"),
    app.injector.instanceOf[HttpVerbs], app.injector.instanceOf[Metrics])

  "getRegistration PAYE" should {
    "return true when the current logged in user has an acceptable number of PAYE clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of PAYE clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForPAYEClientCheck
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringThePAYEClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfPayeClients)
      }
    }
  }

  "getRegistration SA" should {
    "return true when the current logged in user has an acceptable number of SA clients" in {
      givenUserIsAnAgentWithAnAcceptableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of SA clients" in {
      givenUserIsNotAnAgentWithAnAcceptableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForSAClientCheck
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringTheSAClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfSAClients)
      }
    }
  }

  "hasActiveCesaRelationship" should {
    "receive 200 if valid combination passed and relationship exists in Cesa Nino" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")
      givenCleanMetricRegistry()
      await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe true
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET")
    }

    "receive 200 if valid combination passed and relationship exists in Cesa Utr" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")
      givenCleanMetricRegistry()
      await(connector.hasActiveCesaRelationship(Utr("4000000009"), "utr", SaAgentReference("SA6012"))) shouldBe true
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET")
    }

    "receive 403 if valid combination passed and relationship does not exist in Cesa" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")
      givenCleanMetricRegistry()
      await(connector.hasActiveCesaRelationship(Nino("AA123456A"), "nino", SaAgentReference("SA6012"))) shouldBe false
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET")
    }

    "receive 403 if invalid combination passed" in {
      givenABadCombinationAndUserHasRelationshipInCesa("nino", "AB123456A", "SA126013")
      givenCleanMetricRegistry()
      await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET")
    }

    "receive 404 when valid Nino but is not found in DB" in {
      givenAGoodCombinationAndNinoNotFoundInCesa("nino", "AB123456B", "SA126012")
      givenCleanMetricRegistry()
      await(connector.hasActiveCesaRelationship(Nino("AB123456A"), "nino", SaAgentReference("SA126013"))) shouldBe false
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getActiveCesaRelationship-GET")
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
      intercept[Upstream4xxResponse] {
        await(connector.isManuallyAssuredAgent(utr))
      }
    }
    "throw Upstream5xxResponse when agent-assurance responds with 500" in {
      givenManuallyAssuredAgentsReturns(utr.value, 500)
      intercept[Upstream5xxResponse] {
        await(connector.isManuallyAssuredAgent(utr))
      }
    }
    "monitor with metric ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET" in {
      givenCleanMetricRegistry()
      givenAgentIsManuallyAssured(utr.value)

      await(connector.isManuallyAssuredAgent(utr))
      timerShouldExistsAndBeenUpdated("ConsumedAPI-AgentAssurance-getManuallyAssuredAgents-GET")
    }
  }
}