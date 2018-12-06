package uk.gov.hmrc.agentsubscriptionfrontend.repository

import java.time.LocalDate
import java.util.UUID

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.support.MongoApp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ChainedSessionDetailsRepositoryISpec extends UnitSpec with OneAppPerSuite with MongoApp with Eventually {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)
      .configure("Test.mongodb.chainedsessiondetails.ttl" -> 1)

  override implicit lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[ChainedSessionDetailsRepository]

  private val utr = Utr("0123456789")
  protected  val initialDetails =
    InitialDetails(
      utr,
      "AA11AA",
      "My Agency",
      Some("agency@example.com"),
      BusinessAddress(
        "AddressLine1 A",
        Some("AddressLine2 A"),
        Some("AddressLine3 A"),
        Some("AddressLine4 A"),
        Some("AA11AA"),
        "GB")
    )

  val amlsDetails = AMLSDetails("supervisory", "123456789", LocalDate.now())

  private val chainedSessionDetails =
    ChainedSessionDetails(
      KnownFactsResult(utr = utr, postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false, None, None),
      Some(true), Some(initialDetails), Some(amlsDetails)
    )

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "ChainedSessionDetailsRepository" should {

    "create a StashedChainedSessionDetails record" in {
      val result = await(repo.create(chainedSessionDetails))
      result should not be empty

      val stashedChainedSessionDetails = await(repo.find("id" -> result)).head
      stashedChainedSessionDetails should have('id (result), 'chainedSessionDetails (chainedSessionDetails))
      stashedChainedSessionDetails.id.size shouldBe 32
    }

    "find a ChainedSessionDetails by Id" in {
      val id = UUID.randomUUID().toString.substring(0, 8)
      val record = StashedChainedSessionDetails(id, chainedSessionDetails)
      await(repo.insert(record))

      await(repo.findChainedSessionDetails(id)) shouldBe Some(chainedSessionDetails)
    }

    "delete a StashedChainedSessionDetails record by Id" in {
      val id = UUID.randomUUID().toString.substring(0, 8)
      val record = StashedChainedSessionDetails(id, chainedSessionDetails)
      await(repo.insert(record))

      await(repo.delete(id))

      await(repo.find("id" -> id)) shouldBe empty
    }

    "not return any ChainedSessionDetails for an invalid Id" in {
      await(repo.findChainedSessionDetails("INVALID")) shouldBe empty
    }

    "remove a stored StashedChainedSessionDetails record after the configured TTL" ignore {
      await(repo.ensureIndexes)
      val id = await(repo.create(chainedSessionDetails))

      eventually(timeout(scaled(60 second)), interval(60 seconds)) {
        await(repo.findChainedSessionDetails(id)) shouldBe empty

      }
    }
  }
}
