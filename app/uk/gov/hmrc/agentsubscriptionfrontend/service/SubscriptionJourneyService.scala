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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId, ContinueId}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionJourneyService @Inject()(agentSubscriptionConnector: AgentSubscriptionConnector)(implicit ec: ExecutionContext) {

  def getMandatoryJourneyRecord(continueId: ContinueId)(implicit hc: HeaderCarrier): Future[SubscriptionJourneyRecord] =
    for {
      record <- agentSubscriptionConnector.getJourneyByContinueId(continueId)
    } yield extractMandatoryRecord(record)

  def existsJourneyForUtr(utr: Utr)(implicit hc: HeaderCarrier): Future[Boolean] =
    agentSubscriptionConnector.getJourneyByUtr(utr).map(_.isDefined)

  def getJourneyRecord(internalId: AuthProviderId)(implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    for {
      record <- agentSubscriptionConnector.getJourneyById(internalId)
    } yield record

  def getMandatoryJourneyRecord(internalId: AuthProviderId)(implicit hc: HeaderCarrier): Future[SubscriptionJourneyRecord] =
    for {
      record <- agentSubscriptionConnector.getJourneyById(internalId)
    } yield extractMandatoryRecord(record)

  private def extractMandatoryRecord(record: Option[SubscriptionJourneyRecord]): SubscriptionJourneyRecord =
    record match {
      case Some(r) => r
      case None    => throw new RuntimeException("Journey record expected")
    }

  def saveJourneyRecord(subscriptionJourneyRecord: SubscriptionJourneyRecord)(implicit hc: HeaderCarrier): Future[Int] =
    agentSubscriptionConnector.createOrUpdateJourney(subscriptionJourneyRecord)

  def createJourneyRecord(agentSession: AgentSession, agent: Agent)(implicit hc: HeaderCarrier): Future[Either[String, Unit]] = {
    val sjr =
      SubscriptionJourneyRecord
        .fromAgentSession(agentSession, agent.authProviderId, agent.maybeCleanCredsAuthProviderId)
    saveJourneyRecord(sjr) map {
      case 204 => Right(())
      case 409 => Left("the subscription journey record already exists in the database")
    }
  }
}
