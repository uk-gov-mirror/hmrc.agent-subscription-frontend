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

package uk.gov.hmrc.agentsubscriptionfrontend.connectors

import java.time.LocalDate

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscriptionfrontend.util.HttpClientConverter._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentSubscriptionConnector @Inject()(
  http: HttpClient,
  metrics: Metrics,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getJourneyById(internalId: AuthProviderId)(implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET") {
      val url =
        s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/id/${encodePathSegment(internalId.id)}"
      http
        .GET[HttpResponse](url)
        .map(response =>
          response.status match {
            case 200           => Some(Json.parse(response.body).as[SubscriptionJourneyRecord])
            case s if is2xx(s) => None
            case s             => throw UpstreamErrorResponse(response.body, s)
        })
    }

  def getJourneyByContinueId(continueId: ContinueId)(implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByContinueId-GET") {
      val url = s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/continueId/${encodePathSegment(continueId.value)}"
      transformOptionResponse[SubscriptionJourneyRecord](http.GET[HttpResponse](url.toString))
    }

  def getJourneyByUtr(utr: Utr)(implicit hc: HeaderCarrier): Future[Option[SubscriptionJourneyRecord]] =
    monitor(s"ConsumedAPI-Agent-Subscription-getJourneyByUtr-GET") {
      val url = s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/utr/${encodePathSegment(utr.value)}"
      transformOptionResponse[SubscriptionJourneyRecord](http.GET[HttpResponse](url))
    }

  def createOrUpdateJourney(journeyRecord: SubscriptionJourneyRecord)(implicit hc: HeaderCarrier): Future[Int] =
    monitor("ConsumedAPI-Agent-Subscription-createOrUpdate-POST") {
      val path = s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(journeyRecord.authProviderId.id)}"
      http
        .POST[SubscriptionJourneyRecord, HttpResponse](s"${appConfig.agentSubscriptionBaseUrl}$path", journeyRecord)
        .map { response =>
          response.status match {
            case s if is2xx(s) => s
            case CONFLICT      => CONFLICT
            case s =>
              logger.error(s"creating subscription journey record failed for reason: ${response.body}")
              throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier): Future[Option[Registration]] =
    monitor(s"ConsumedAPI-Agent-Subscription-hasAcceptableNumberOfClients-GET") {
      val url = getRegistrationUrlFor(utr, postcode)
      http.GET[Option[Registration]](url)
    }

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: CompanyRegistrationNumber)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchCorporationTaxUtrWithCrn-GET") {
      val url =
        s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/corporation-tax-utr/${encodePathSegment(utr.value)}/crn/${encodePathSegment(crn.value)}"

      http
        .GET[HttpResponse](url.toString)
        .map { response =>
          response.status match {
            case OK            => true
            case s if is2xx(s) => false
            case NOT_FOUND     => false
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def matchVatKnownFacts(vrn: Vrn, vatRegistrationDate: LocalDate)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-matchVatKnownFacts-GET") {
      val url =
        s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/vat-known-facts/vrn/${encodePathSegment(vrn.value)}/dateOfRegistration/${encodePathSegment(vatRegistrationDate.toString)}"

      http
        .GET[HttpResponse](url.toString)
        .map { response =>
          response.status match {
            case OK            => true
            case s if is2xx(s) => false
            case NOT_FOUND     => false
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  def subscribeAgencyToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST") {
      transformResponse[JsValue](http.POST[SubscriptionRequest, HttpResponse](subscriptionUrl, subscriptionRequest))
        .map(js => (js \ "arn").as[Arn])
    }

  def completePartialSubscription(details: CompletePartialSubscriptionBody)(implicit hc: HeaderCarrier): Future[Arn] =
    monitor(s"ConsumedAPI-Agent-Subscription-completePartialAgencySubscriptionToMtd-PUT") {
      transformResponse[JsValue](http.PUT[CompletePartialSubscriptionBody, HttpResponse](subscriptionUrl, details))
        .map(js => (js \ "arn").as[Arn])
    }

  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[DesignatoryDetails] =
    monitor(s"ConsumedAPI-Agent-Subscription-getDesignatoryDetails-GET") {
      val url =
        s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/citizen-details/${nino.value}/designatory-details"
      transformResponse[DesignatoryDetails](http.GET[HttpResponse](url))
        .recover {
          case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND => DesignatoryDetails()
        }
    }

  def companiesHouseKnownFactCheck(crn: CompanyRegistrationNumber, surname: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    monitor(s"ConsumedAPI-Agent-Subscription-getCompanyOfficers-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      val encodedName = UriEncoding.encodePathSegment(surname.toUpperCase, "UTF-8")
      val url = s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/companies-house-api-proxy/company/$encodedCrn/officers/$encodedName"
      http
        .GET[HttpResponse](url)
        .map { response =>
          response.status match {
            case OK            => true
            case s if is2xx(s) => false
            case NOT_FOUND     => false
            case s             => throw UpstreamErrorResponse(response.body, s)
          }
        }
    }

  private val subscriptionUrl = s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription"

  private def getRegistrationUrlFor(utr: Utr, postcode: String) =
    s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/registration/${encodePathSegment(utr.value)}/postcode/${encodePathSegment(postcode)}"
}
