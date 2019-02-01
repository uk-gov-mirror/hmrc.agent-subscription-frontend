/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscriptionfrontend.service

import play.api.libs.json.{JsValue, Reads, Writes}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, InitialDetails, KnownFactsResult, MappingEligibility}
import uk.gov.hmrc.http.cache.client.{CacheMap, NoSessionException, SessionCache}
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.SessionId

class SessionStoreServiceSpec extends UnitSpec {

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("sessionId123456")))

  "SessionStoreService" should {
    val initialDetails = InitialDetails(
      Utr("9876543210"),
      "AA11AA",
      "My Agency",
      Some("agency@example.com"),
      BusinessAddress(
        "AddressLine1 A",
        Some("AddressLine2 A"),
        Some("AddressLine3 A"),
        Some("AddressLine4 A"),
        Some("AA1AA"),
        "GB")
    )
    val knownFactsResult =
      KnownFactsResult(
        Utr("9876543210"),
        "AA11AA",
        "Test organisation name",
        isSubscribedToAgentServices = true,
        Some(
          BusinessAddress(
            "AddressLine1 A",
            Some("AddressLine2 A"),
            Some("AddressLine3 A"),
            Some("AddressLine4 A"),
            Some("AA1AA"),
            "GB")),
        Some("someone@example.com")
      )

    "store known facts" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.cacheKnownFactsResult(knownFactsResult))

      await(store.fetchKnownFactsResult) shouldBe Some(knownFactsResult)
    }

    "return None when no known facts have been stored" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.fetchKnownFactsResult) shouldBe None
    }

    "store initial details" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.cacheInitialDetails(initialDetails))

      await(store.fetchInitialDetails) shouldBe Some(initialDetails)
    }

    "return None when no initial details have been stored" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.fetchInitialDetails) shouldBe None
    }

    "store continue url" in {
      val store = new SessionStoreService(new TestSessionCache())

      val url = ContinueUrl("http://localhost:9000/agent-mapping")

      await(store.cacheContinueUrl(url))

      await(store.fetchContinueUrl) shouldBe Some(url)
    }

    "return None when no continue url have been stored" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.fetchContinueUrl) shouldBe None
    }

    "store mapping eligibility" when {
      "mapping eligibility is true" in {
        val store = new SessionStoreService(new TestSessionCache())

        await(store.cacheMappingEligible(true))
        await(store.fetchMappingEligible) shouldBe Some(true)
      }
      "mapping eligibility is false" in {
        val store = new SessionStoreService(new TestSessionCache())

        await(store.cacheMappingEligible(false))
        await(store.fetchMappingEligible) shouldBe Some(false)
      }
    }

    "return None when no mapping eligibility has been stored" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.fetchMappingEligible) shouldBe None
    }

    "remove the underlying storage for the current session when remove is called" in {
      val store = new SessionStoreService(new TestSessionCache())

      await(store.cacheKnownFactsResult(knownFactsResult))
      await(store.cacheInitialDetails(initialDetails))
      await(store.cacheMappingEligible(true))

      val url = ContinueUrl("http://localhost:9000/agent-mapping")

      await(store.cacheContinueUrl(url))

      await(store.remove())

      await(store.fetchKnownFactsResult) shouldBe None
      await(store.fetchInitialDetails) shouldBe None
      await(store.fetchMappingEligible) shouldBe None
      await(store.fetchContinueUrl) shouldBe None
    }
  }

}

class TestSessionCache extends SessionCache {
  override def defaultSource = ???
  override def baseUri = ???
  override def domain = ???
  override def http = ???

  private val store = mutable.Map[String, JsValue]()

  private val noSession = Future.failed[String](NoSessionException)

  private def testCacheId(implicit hc: HeaderCarrier): Future[String] =
    hc.sessionId.fold(noSession)(c => Future.successful(c.value))

  override def cache[A](
    formId: String,
    body: A)(implicit wts: Writes[A], hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
    testCacheId.map { c =>
      store.put(formId, wts.writes(body))
      CacheMap(c, store.toMap)
    }

  override def fetch()(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Option[CacheMap]] =
    testCacheId.map(c => Some(CacheMap(c, store.toMap)))

  override def fetchAndGetEntry[T](
    key: String)(implicit hc: HeaderCarrier, rds: Reads[T], executionContext: ExecutionContext): Future[Option[T]] =
    Future {
      store.get(key).flatMap(jsValue => rds.reads(jsValue).asOpt)
    }

  override def remove()(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[HttpResponse] =
    Future {
      store.clear()
      null
    }
}
