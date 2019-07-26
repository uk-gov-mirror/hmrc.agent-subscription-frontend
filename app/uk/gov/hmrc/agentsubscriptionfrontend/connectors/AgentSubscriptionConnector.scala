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

package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.net.URL
import java.time.LocalDate

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentSubscriptionConnector @Inject()(
  @Named("agent-subscription-baseUrl") baseUrl: URL,
  http: HttpGet with HttpPost with HttpPut with HttpDelete,
  metrics: Metrics)(implicit ec: ExecutionContext)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getJourneyById(internalId: AuthProviderId)(
    implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET") {
      val url =
        new URL(baseUrl, s"/agent-subscription/subscription/journey/id/${encodePathSegment(internalId.id)}")
      http
        .GET[HttpResponse](url.toString)
        .map(response =>
          response.status match {
            case 200 => Some(Json.parse(response.body).as[SubscriptionJourneyRecord])
            case 204 => None
        })
    }

  def getJourneyByContinueId(continueId: ContinueId)(
    implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByContinueId-GET") {
      val url =
        new URL(baseUrl, s"/agent-subscription/subscription/journey/continueId/${encodePathSegment(continueId.value)}")
      http.GET[Option[SubscriptionJourneyRecord]](url.toString)
    }

  def createOrUpdateJourney(journeyRecord: SubscriptionJourneyRecord)(implicit hc: HeaderCarrier): Future[Unit] =
    monitor("ConsumedAPI-Agent-Subscription-createOrUpdate-POST") {
      val path =
        s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(journeyRecord.authProviderId.id)}"
      http
        .POST[SubscriptionJourneyRecord, HttpResponse](new URL(baseUrl, path).toString, journeyRecord)
        .map(handleUpdateJourneyResponse(_, path))
    }

  def deleteJourney(authProviderId: AuthProviderId)(implicit hc: HeaderCarrier): Future[Unit] =
    monitor("ConsumedAPI-Agent-Subscription-delete-DELETE") {
      val path = s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(authProviderId.id)}"
      val url = new URL(baseUrl, path)
      http.DELETE[HttpResponse](url.toString).map(handleUpdateJourneyResponse(_, path))
    }

  private def handleUpdateJourneyResponse(httpResponse: HttpResponse, path: String): Unit =
    httpResponse.status match {
      case 204    => ()
      case status => throw new RuntimeException(s"POST to $path returned $status")
    }

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier): Future[Option[Registration]] =
    monitor(s"ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
      val url = getRegistrationUrlFor(utr, postcode)
      http.GET[Option[Registration]](url)
    }

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: CompanyRegistrationNumber)(
    implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
      val url = new URL(
        baseUrl,
        s"/agent-subscription/corporation-tax-utr/${encodePathSegment(utr.value)}/crn/${encodePathSegment(crn.value)}")

      http
        .GET[HttpResponse](url.toString)
        .map(_.status == 200)
        .recover {
          case _: NotFoundException => false
        }
    }

  def matchVatKnownFacts(vrn: Vrn, vatRegistrationDate: LocalDate)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
      val url = new URL(
        baseUrl,
        s"/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(vatRegistrationDate.toString)}"
      )

      http
        .GET[HttpResponse](url.toString)
        .map(_.status == 200)
        .recover {
          case _: NotFoundException => false
        }
    }

  def subscribeAgencyToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
      http.POST[SubscriptionRequest, JsValue](subscriptionUrl.toString, subscriptionRequest) map { js =>
        (js \ "arn").as[Arn]
      }
    }

  def completePartialSubscription(details: CompletePartialSubscriptionBody)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
      http.PUT[CompletePartialSubscriptionBody, JsValue](subscriptionUrl.toString, details).map { js =>
        (js \ "arn").as[Arn]
      }
    }

  def matchCitizenDetails(citizenDetailsRequest: CitizenDetailsRequest)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-getCitizenDetails-POST") {
      val endpoint = s"/agent-subscription/citizen-details"
      http
        .POST[CitizenDetailsRequest, HttpResponse](new URL(baseUrl, endpoint).toString, citizenDetailsRequest)
        .map(_.status == 200)
        .recover {
          case e: BadRequestException => false
          case f: NotFoundException   => false
        }
    }

  private val subscriptionUrl = new URL(baseUrl, s"/agent-subscription/subscription")

  private def getRegistrationUrlFor(utr: Utr, postcode: String) =
    new URL(
      baseUrl,
      s"/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}").toString
}
