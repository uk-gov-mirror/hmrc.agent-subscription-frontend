package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.HttpVerbs
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.HeaderCarrier
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
      givenUtrReturnedInR2DWList(utr.value)
      await(connector.isR2DWAgent(utr)) shouldBe true
    }
    "return false is utr not found in r2dw list" in {
      givenUtrReturnedInR2DWList(utr.value)
      await(connector.isR2DWAgent(Utr("1000000009"))) shouldBe false
    }
    "return false is r2dw list is empty" in {
      givenR2DWListIsEmpty
      await(connector.isR2DWAgent(Utr("1000000009"))) shouldBe false
    }
    "return illegal state exception when " in {
      given404ReturnedForR2dw
      intercept[IllegalStateException] {
        await(connector.isR2DWAgent(Utr("1234567")))
      }
    }

  }
}