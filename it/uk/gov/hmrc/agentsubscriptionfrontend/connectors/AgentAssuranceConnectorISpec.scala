package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.config.WSHttp
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class AgentAssuranceConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport{
  private implicit val hc = HeaderCarrier()

  private lazy val connector = new AgentAssuranceConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "getRegistration PAYE" should {
    "return true when the current logged in user has an acceptable number of PAYE clients" in {
      givenUserIsAnAgentWithAnAccetableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of PAYE clients" in {
      givenUserIsNotAnAgentWithAnAccetableNumberOfPAYEClients
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForPAYEClientCheck
      await(connector.hasAcceptableNumberOfPayeClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringhThePAYEClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfPayeClients)
      }
    }
  }

  "getRegistration SA" should {
    "return true when the current logged in user has an acceptable number of SA clients" in {
      givenUserIsAnAgentWithAnAccetableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe true
    }

    "return false when the current logged in user does not have an acceptable number of SA clients" in {
      givenUserIsNotAnAgentWithAnAccetableNumberOfSAClients
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "return false when the current user is not authenticated" in {
      givenUserIsNotAuthenticatedForSAClientCheck
      await(connector.hasAcceptableNumberOfSAClients) shouldBe false
    }

    "throw an exception when appropriate" in {
      givenAnExceptionOccursDuringhTheSAClientCheck
      intercept[Exception] {
        await(connector.hasAcceptableNumberOfSAClients)
      }
    }
  }
}
