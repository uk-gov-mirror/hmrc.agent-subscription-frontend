/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.Logging
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentsubscriptionfrontend.util._

import scala.concurrent.{ExecutionContext, Future}

trait SessionCache[T] extends MongoSessionStore[T] with Logging {

  def fetch(implicit hc: HeaderCarrier, reads: Reads[T], ec: ExecutionContext): Future[Option[T]] =
    get.flatMap {
      case Right(input) => input.toFuture
      case Left(error) =>
        logger.warn(error)
        Future.failed(new RuntimeException(error))
    }

  def fetchAndClear(implicit hc: HeaderCarrier, reads: Reads[T], ec: ExecutionContext): Future[Option[T]] = {
    val result = for {
      cache <- get
      _     <- delete()
    } yield cache

    result.flatMap {
      case Right(input) => input.toFuture
      case Left(error) =>
        logger.warn(error)
        Future.failed(new RuntimeException(error))
    }
  }

  def save(input: T)(implicit hc: HeaderCarrier, writes: Writes[T], ec: ExecutionContext): Future[T] =
    store(input).flatMap {
      case Right(_) => input.toFuture
      case Left(error) =>
        logger.warn(error)
        Future.failed(new RuntimeException(error))
    }

  def hardGet(implicit hc: HeaderCarrier, reads: Reads[T], ec: ExecutionContext): Future[T] =
    fetch.map {
      case Some(entry) => entry
      case None =>
        throw new IllegalStateException("Cached session state expected but not found")
    }

}
