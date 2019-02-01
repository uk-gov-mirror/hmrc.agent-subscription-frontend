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

package uk.gov.hmrc.agentsubscriptionfrontend.repository

import java.util.UUID

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.ChainedSessionDetails
import uk.gov.hmrc.agentsubscriptionfrontend.repository.StashedChainedSessionDetails.StashedChainnedSessionId
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ChainedSessionDetailsRepository @Inject()(appConfig: AppConfig, mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[StashedChainedSessionDetails, BSONObjectID](
      "chained-session",
      mongoComponent.mongoConnector.db,
      StashedChainedSessionDetails.format,
      ReactiveMongoFormats.objectIdFormats) {

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("id" -> IndexType.Ascending), name = Some("idUnique"), unique = true),
      Index(
        key = Seq("createdDate" -> IndexType.Ascending),
        name = Some("createDate"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> appConfig.chainedSessionDetailsTtl)
      )
    )

  def findChainedSessionDetails(id: StashedChainnedSessionId)(
    implicit ec: ExecutionContext): Future[Option[ChainedSessionDetails]] =
    find("id" -> id).map(_.headOption.map(_.chainedSessionDetails))

  def create(chainedSessionDetails: ChainedSessionDetails)(
    implicit ec: ExecutionContext): Future[StashedChainnedSessionId] = {
    val id: StashedChainnedSessionId = UUID.randomUUID().toString.replace("-", "")
    insert(StashedChainedSessionDetails(id, chainedSessionDetails)).map(_ => id)
  }

  def delete(id: StashedChainnedSessionId)(implicit ec: ExecutionContext): Future[Unit] =
    remove("id" -> id).map(_ => ())
}

case class StashedChainedSessionDetails(
  id: StashedChainnedSessionId,
  chainedSessionDetails: ChainedSessionDetails,
  createdDate: DateTime = DateTime.now)

object StashedChainedSessionDetails {
  type StashedChainnedSessionId = String
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val format = Json.format[StashedChainedSessionDetails]
}
