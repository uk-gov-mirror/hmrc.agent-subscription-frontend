package uk.gov.hmrc.agentsubscriptionfrontend.connectors
import java.net.URL

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentServicesAccountStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentServicesAccountConnectorSpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector =
    new AgentServicesAccountConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpClient],
      app.injector.instanceOf[Metrics])


  "getEmail" should {
    behave like testGetEndpoint(connector.getAgencyEmail())
  }


  def testGetEndpoint(method: => Future[AgencyEmail]) = {
    s"return an email" in {
      givenGetEmailStub
      await(method) shouldBe AgencyEmail("test@gmail.com")
    }

    s"return no email despite a record found" in {
      givenNoEmailStub
      intercept[MismatchedInputException] {
        await(method)
      }
    }

    s"throw an exception when no record is found" in {
      givenNotFoundEmailStub
      intercept[NotFoundException] {
        await(method)
      }
    }

    s"throw an exception from a Upstream5xx Response" in {
      givenUpstream5xxEmailStub
      intercept[Upstream5xxResponse] {
        await(method)
      }
    }
  }
}
