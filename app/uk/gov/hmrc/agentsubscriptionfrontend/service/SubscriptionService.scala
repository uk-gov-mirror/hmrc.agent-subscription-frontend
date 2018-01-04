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

package uk.gov.hmrc.agentsubscriptionfrontend.service

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.http.Status
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.SubscriptionDetails
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector) {

  def subscribeAgencyToMtd(subscriptionDetails: SubscriptionDetails)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Int, Arn]] = {
    val address = if (subscriptionDetails.address.countryCode != "GB") {
      Logger.warn(s"Non-GB country code chosen by user for UTR ${subscriptionDetails.utr.value}. " +
        s"Overriding with GB. A better fix for this is coming in APB-1288.")
      subscriptionDetails.address.copy(countryCode = "GB")
    } else {
      subscriptionDetails.address
    }

    val request = SubscriptionRequest(subscriptionDetails.utr, SubscriptionRequestKnownFacts(subscriptionDetails.knownFactsPostcode), Agency(
      name = subscriptionDetails.name,
      email = subscriptionDetails.email,
      telephone = subscriptionDetails.telephone,
      address = address)
    )

    agentSubscriptionConnector.subscribeAgencyToMtd(request) map { x =>
      Right(x)
    } recover {
      case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode => Left(e.upstreamResponseCode)
      case e => throw e
    }
  }
}
