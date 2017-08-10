package uk.gov.hmrc.agentsubscriptionfrontend.repository

import java.util.UUID

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.support.MongoApp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class KnownFactsResultRepositoryISpec extends UnitSpec with OneAppPerSuite with MongoApp with Eventually {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)
      .configure("Test.mongodb.knownfactsresult.ttl" -> 1)

  override implicit lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[KnownFactsResultMongoRepository]

  private val utr = Utr("0123456789")
  private val knownFactsResult = KnownFactsResult(utr = utr,
    postcode = "AA11AA", taxpayerName = "My Agency", isSubscribedToAgentServices = false)

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "KnownFactsResultMongoRepository" should {

    "create a StashedKnownFactsResult record" in {
      val result = await(repo.create(knownFactsResult))

      result should not be empty

      await(repo.find("id" -> result)).head should have(
        'id (result),
        'knownFactsResult (knownFactsResult)
      )
    }

    "find a KnownFactsResult record by Id" in {
      val id = UUID.randomUUID().toString.substring(0, 8)
      val record = StashedKnownFactsResult(id, knownFactsResult)
      await(repo.insert(record))

      val result = await(repo.findKnownFactsResult(id))

      result shouldBe Some(knownFactsResult)
    }

    "delete a StashedKnownFactsResult record by Id" in {
      val id = UUID.randomUUID().toString.substring(0, 8)
      val record = StashedKnownFactsResult(id, knownFactsResult)
      await(repo.insert(record))

      await(repo.delete(id))

      await(repo.find("id" -> id)) shouldBe empty
    }

    "not return any KnownFactsResult record for an invalid Id" in {
      val result = await(repo.findKnownFactsResult("INVALID"))

      result shouldBe empty
    }

    "remove a stored KnownFactsResult record after the configured TTL" ignore {
      await(repo.ensureIndexes)
      val id = await(repo.create(knownFactsResult))

      eventually(timeout(scaled(60 second)), interval(60 seconds)) {
        await(repo.findKnownFactsResult(id)) shouldBe empty

      }
    }
  }
}

