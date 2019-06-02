package uk.gov.hmrc.agentsubscriptionfrontend.connectors
import java.net.URL

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentServicesAccountStub._
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, MetricTestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentServicesAccountSpec extends BaseISpec with MetricTestSupport {

  private implicit val hc = HeaderCarrier()

  private lazy val connector =
    new AgentServicesAccountConnector(
      new URL(s"http://localhost:$wireMockPort"),
      app.injector.instanceOf[HttpGet],
      app.injector.instanceOf[Metrics])


  "getEmail" should {
    behave like testGetEndpoint(connector.getAgencyEmail())
  }


  def testGetEndpoint(method: => Future[Option[String]]) = {
    s"return an email" in {
      givenGetEmailStub
      await(method) shouldBe Some("test@gmail.com")
    }

    s"return no email despite a record found " in {
      givenNoEmailStub
      await(method) shouldBe None
    }

    s"throw an exception when no record is found" in {
      givenNotFoundEmailStub
      intercept[Exception] {
        await(method)
      }
    }
  }
}
