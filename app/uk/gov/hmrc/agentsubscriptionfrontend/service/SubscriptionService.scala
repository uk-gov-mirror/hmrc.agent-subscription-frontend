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

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}

import scala.concurrent.{ExecutionContext, Future}

case class SubscriptionReturnedHttpError(httpStatusCode: Int) extends Product with Serializable

object SubscriptionState extends Enumeration {
  type SubscriptionState = Value
  val Unsubscribed, SubscribedButNotEnrolled, SubscribedAndEnrolled, NoRegistrationFound = Value
}

case class SubscriptionProcess(state: SubscriptionState.Value, details: Option[Registration])

@Singleton
class SubscriptionService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector) {

  import SubscriptionDetails._

  def subscribe(details: InitialDetails, address: DesAddress, mayBeAmlsDetails: Option[AMLSDetails])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
    val subscriptionDetails = mapper(details, address, mayBeAmlsDetails)
    subscribeAgencyToMtd(subscriptionDetails) map {
      case Right(arn) => Right((arn, subscriptionDetails.name))
      case Left(x)    => Left(SubscriptionReturnedHttpError(x))
    }
  }

  def subscribeAgencyToMtd(subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[Int, Arn]] = {
    val address = if (subscriptionDetails.address.countryCode != "GB") {
      Logger(getClass).warn(
        s"Non-GB country code chosen by user for UTR ${subscriptionDetails.utr.value}. " +
          s"Overriding with GB. A better fix for this is coming in APB-1288.")
      subscriptionDetails.address.copy(countryCode = "GB")
    } else {
      subscriptionDetails.address
    }

    val request = SubscriptionRequest(
      subscriptionDetails.utr,
      SubscriptionRequestKnownFacts(subscriptionDetails.knownFactsPostcode),
      Agency(name = subscriptionDetails.name, email = subscriptionDetails.email, address = address),
      subscriptionDetails.amlsDetails
    )

    agentSubscriptionConnector.subscribeAgencyToMtd(request).map[Either[Int, Arn]] { arn =>
      Right(arn)
    } recover {
      case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode =>
        Left(e.upstreamResponseCode)
      case e => throw e
    }
  }

  def completePartialSubscription(utr: Utr, businessPostCode: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Arn] =
    agentSubscriptionConnector
      .completePartialSubscription(
        CompletePartialSubscriptionBody(utr, SubscriptionRequestKnownFacts(businessPostCode)))
      .recover {
        case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode =>
          Logger.warn(s"Eligibility checks failed for partialSubscriptionFix, with status: ${e.upstreamResponseCode}")
          throw e
      }

  def getSubscriptionStatus(utr: Utr, postcode: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[SubscriptionProcess] =
    agentSubscriptionConnector.getRegistration(utr, postcode).map {

      case Some(reg) if reg.isSubscribedToAgentServices =>
        SubscriptionProcess(SubscriptionState.SubscribedAndEnrolled, Some(reg))

      case Some(Registration(None, _, _, _, _)) =>
        throw new IllegalStateException(s"The agency with UTR ${utr.value} has a missing organisation/individual name.")

      case Some(reg) if !reg.isSubscribedToAgentServices && reg.isSubscribedToETMP =>
        SubscriptionProcess(SubscriptionState.SubscribedButNotEnrolled, Some(reg))

      case Some(reg) if !reg.isSubscribedToAgentServices && !reg.isSubscribedToETMP =>
        SubscriptionProcess(SubscriptionState.Unsubscribed, Some(reg))

      case None => SubscriptionProcess(SubscriptionState.NoRegistrationFound, None)
    }

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: CompanyRegistrationNumber)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Boolean] =
    agentSubscriptionConnector.matchCorporationTaxUtrWithCrn(utr, crn)

  def matchVatKnownFacts(vrn: Vrn, vatRegistrationDate: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Boolean] =
    agentSubscriptionConnector.matchVatKnownFacts(vrn, vatRegistrationDate)

}
