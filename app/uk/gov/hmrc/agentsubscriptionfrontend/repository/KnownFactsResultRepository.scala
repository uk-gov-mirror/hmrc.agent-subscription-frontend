/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.repository

import java.util.UUID
import javax.inject.{Inject, Named, Singleton}

import org.joda.time.DateTime
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFactsResult
import uk.gov.hmrc.agentsubscriptionfrontend.repository.StashedKnownFactsResult.StashedKnownFactsResultId
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KnownFactsResultMongoRepository @Inject()(@Named("mongodb.knownfactsresult.ttl") ttl: Int, mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[StashedKnownFactsResult, BSONObjectID]("agent-known-facts-results",
    mongoComponent.mongoConnector.db,
    StashedKnownFactsResult.format,
    ReactiveMongoFormats.objectIdFormats) {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("id" -> IndexType.Ascending),
      name = Some("idUnique"),
      unique = true),
    Index(
      key = Seq("createdDate" -> IndexType.Ascending),
      name = Some("createDate"),
      unique = false,
      options = BSONDocument("expireAfterSeconds" -> ttl)
    )
  )

  def findKnownFactsResult(id: StashedKnownFactsResultId)(implicit ec: ExecutionContext): Future[Option[KnownFactsResult]] =
    find("id" -> id).map(_.headOption.map(_.knownFactsResult))

  def create(knownFactsResult: KnownFactsResult)(implicit ec: ExecutionContext): Future[StashedKnownFactsResultId] = {
    val id: StashedKnownFactsResultId = UUID.randomUUID().toString.replace("-", "").take(8)
    insert(StashedKnownFactsResult(id, knownFactsResult)).map(_ => id)
  }

  def delete(id: StashedKnownFactsResultId)(implicit ec: ExecutionContext): Future[Unit] =
    remove("id" -> id).map(_ => ())
}

case class StashedKnownFactsResult(id: StashedKnownFactsResultId, knownFactsResult: KnownFactsResult, createdDate: DateTime = DateTime.now)

object StashedKnownFactsResult {
  type StashedKnownFactsResultId = String
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val format = Json.format[StashedKnownFactsResult]
}
