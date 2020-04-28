/*
 * Copyright 2020 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status
import play.api.i18n.Lang
import play.api.mvc.Result
import play.api.mvc.Results.{Conflict, Redirect}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.{ExecutionContext, Future}

case class SubscriptionReturnedHttpError(httpStatusCode: Int) extends Product with Serializable

sealed abstract class SubscriptionState
case object Unsubscribed extends SubscriptionState
case object SubscribedButNotEnrolled extends SubscriptionState
case object SubscribedAndEnrolled extends SubscriptionState
case object NoRegistrationFound extends SubscriptionState

case class SubscriptionProcess(state: SubscriptionState, details: Option[Registration])

@Singleton
class SubscriptionService @Inject()(
  agentSubscriptionConnector: AgentSubscriptionConnector,
  sessionStoreService: SessionStoreService,
  subscriptionJourneyService: SubscriptionJourneyService,
  val metrics: Metrics)
    extends Monitoring {

  import SubscriptionDetails._

  def subscribe(utr: Utr, postcode: Postcode, agency: Agency, langForEmail: Option[Lang], amlsData: Option[AmlsData])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SubscriptionReturnedHttpError, (Arn, String)]] = {
    val subscriptionDetails = mapper(utr, postcode, agency, amlsData)
    subscribeAgencyToMtd(subscriptionDetails, langForEmail) map {
      case Right(arn) => Right((arn, subscriptionDetails.name))
      case Left(x)    => Left(SubscriptionReturnedHttpError(x))
    }
  }

  def subscribeAgencyToMtd(subscriptionDetails: SubscriptionDetails, langForEmail: Option[Lang])(
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
      langForEmail,
      subscriptionDetails.amlsData match {
        case Some(amlsData) => amlsData.amlsDetails
        case None           => None
      }
    )

    agentSubscriptionConnector.subscribeAgencyToMtd(request).map[Either[Int, Arn]] { arn =>
      Right(arn)
    } recover {
      case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode =>
        Logger.warn("Upstream error (in agent-subscription): see agent-subscription log for details")
        Left(e.upstreamResponseCode)
      case e: Upstream5xxResponse if e.message contains("AGENT_TERMINATED") =>
        Logger.warn(s"Terminated agent is trying to re-subscribe ${e.message}")
        Left(e.upstreamResponseCode)
      case e =>
        Logger.error("Upstream error (in agent-subscription): see agent-subscription log for details", e)
        throw e
    }
  }

  def completePartialSubscription(utr: Utr, businessPostCode: Postcode)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Arn] =
    agentSubscriptionConnector
      .completePartialSubscription(
        CompletePartialSubscriptionBody(utr, SubscriptionRequestKnownFacts(businessPostCode.value)))
      .recover {
        case e: Upstream4xxResponse if Seq(Status.FORBIDDEN, Status.CONFLICT) contains e.upstreamResponseCode =>
          Logger.warn(s"Eligibility checks failed for partialSubscriptionFix, with status: ${e.upstreamResponseCode}")
          throw e
      }

  def completePartialSubscriptionAndGoToComplete(utr: Utr, businessPostCode: Postcode)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    completePartialSubscription(utr, businessPostCode).map { _ =>
      mark("Count-Subscription-PartialSubscriptionCompleted")
      Redirect(routes.SubscriptionController.showSubscriptionComplete())
    } recover {
      case e: Upstream5xxResponse if e.message contains("AGENT_TERMINATED") =>
        Logger.warn(s"Terminated agent has isASAgent flag and is trying to re-subscribe ${e.message}")
        Redirect(routes.StartController.showCannotCreateAccount())
    }

  def redirectAfterGGCredsCreatedBasedOnStatus(continueId: ContinueId, agent: Agent)(
    implicit hc: HeaderCarrier,
    ex: ExecutionContext): Future[Result] =
    for {
      record             <- subscriptionJourneyService.getMandatoryJourneyRecord(continueId)
      subscriptionStatus <- getSubscriptionStatus(record.businessDetails.utr, record.businessDetails.postcode)
      //if user is partially subscribed when they come back with a new user ID can complete partial subscription with clean creds
      completePartialSubscriptionOrTaskList <- subscriptionStatus match {
                                                case SubscriptionProcess(SubscribedButNotEnrolled, Some(_)) =>
                                                  completePartialSubscriptionAndGoToComplete(
                                                    record.businessDetails.utr,
                                                    record.businessDetails.postcode)

                                                case _ =>
                                                  subscriptionJourneyService
                                                    .saveJourneyRecord(
                                                      record.copy(
                                                        cleanCredsAuthProviderId = Some(agent.authProviderId)))
                                                    .map { _ =>
                                                      Redirect(routes.TaskListController.showTaskList())
                                                    }
                                              }
    } yield completePartialSubscriptionOrTaskList

  def getSubscriptionStatus(utr: Utr, postcode: Postcode)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[SubscriptionProcess] =
    agentSubscriptionConnector.getRegistration(utr, postcode.value).map {

      case Some(reg) if reg.isSubscribedToAgentServices =>
        SubscriptionProcess(SubscribedAndEnrolled, Some(reg))

      case Some(Registration(None, _, _, _, _)) =>
        throw new IllegalStateException(s"The agency with UTR ${utr.value} has a missing organisation/individual name.")

      case Some(reg) if !reg.isSubscribedToAgentServices && reg.isSubscribedToETMP =>
        SubscriptionProcess(SubscribedButNotEnrolled, Some(reg))

      case Some(reg) if !reg.isSubscribedToAgentServices && !reg.isSubscribedToETMP =>
        SubscriptionProcess(Unsubscribed, Some(reg))

      case None => SubscriptionProcess(NoRegistrationFound, None)
    }

  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails] =
    agentSubscriptionConnector.getDesignatoryDetails(nino)

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: CompanyRegistrationNumber)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Boolean] =
    agentSubscriptionConnector.matchCorporationTaxUtrWithCrn(utr, crn)

  def matchVatKnownFacts(vrn: Vrn, vatRegistrationDate: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Boolean] =
    agentSubscriptionConnector.matchVatKnownFacts(vrn, vatRegistrationDate)

  def handlePartiallySubscribedAndRedirect(agent: Agent, agentSession: AgentSession)(
    whenNotPartiallySubscribed: => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    val utr = agentSession.utr.getOrElse(Utr(""))
    val postcode = agentSession.postcode.getOrElse(Postcode(""))
    for {
      subscriptionProcess <- getSubscriptionStatus(utr, postcode)
      result <- if (subscriptionProcess.state == SubscribedButNotEnrolled)
                 agent.cleanCredsFold(isDirty = {
                   subscriptionJourneyService
                     .createJourneyRecord(agentSession, agent)
                     .map {
                       case Right(()) => Redirect(routes.SubscriptionController.showSignInWithNewID())
                       case Left(msg) => Logger.warn(msg); Conflict
                     }
                 })(
                   isClean = completePartialSubscriptionAndGoToComplete(utr, postcode)
                 )
               else whenNotPartiallySubscribed
    } yield result
  }
}
