/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models.{InitialDetails, KnownFactsResult}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TestSessionStoreService extends SessionStoreService(null) {
  var knownFactsResult: Option[KnownFactsResult] = None
  var removeCalled: Boolean = false

  var initialDetails: Option[InitialDetails] = None

  def reset(): Unit = {
    knownFactsResult = None
    initialDetails = None
    removeCalled = false
  }

  override def fetchKnownFactsResult(implicit hc: HeaderCarrier): Future[Option[KnownFactsResult]] = Future successful knownFactsResult

  override def cacheKnownFactsResult(knownFactsResult: KnownFactsResult)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(
      this.knownFactsResult = Some(knownFactsResult)
    )

  override def fetchInitialDetails(implicit hc: HeaderCarrier): Future[Option[InitialDetails]] = Future.successful(initialDetails)

  override def cacheInitialDetails(details: InitialDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future.successful(
      this.initialDetails = Some(details)
    )

  override def remove()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future {
      reset()
      removeCalled = true
    }
}
