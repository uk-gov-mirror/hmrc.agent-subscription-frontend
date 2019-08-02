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

package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{Utr, Vrn}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

object AgentSubscriptionJourneyStub {

  def givenNoSubscriptionJourneyRecordExists(authProviderId: AuthProviderId): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.NO_CONTENT)
        )
    )

  def givenSubscriptionJourneyRecordExists(authProviderId: AuthProviderId, subscriptionJourneyRecord: SubscriptionJourneyRecord): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(subscriptionJourneyRecord).toString()))
    )

  def givenSubscriptionJourneyRecordExists(continueId: ContinueId, subscriptionJourneyRecord: SubscriptionJourneyRecord): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/continueId/${encodePathSegment(continueId.value)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(subscriptionJourneyRecord).toString()))
    )

  def givenSubscriptionJourneyRecordExists(utr: Utr, subscriptionJourneyRecord: SubscriptionJourneyRecord): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/utr/${encodePathSegment(utr.value)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(subscriptionJourneyRecord).toString()))
    )

  def givenNoSubscriptionJourneyRecordExists(utr: Utr): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/utr/${encodePathSegment(utr.value)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.NO_CONTENT)))

  def givenNoSubscriptionJourneyRecordExists(continueId: ContinueId): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/agent-subscription/subscription/journey/continueId/${encodePathSegment(continueId.value)}"))
        .willReturn(
          aResponse()
            .withStatus(Status.NO_CONTENT)))

  def givenSubscriptionRecordCreated(authProviderId: AuthProviderId, subscriptionJourneyRecord: SubscriptionJourneyRecord) = {
    stubFor(
      post(
        urlEqualTo(s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(authProviderId.id)}"))
        .withRequestBody(equalToJson(
          Json.toJson(subscriptionJourneyRecord).toString(), true, true ))
        .willReturn(aResponse().withStatus(Status.NO_CONTENT))
    )
  }

  def givenSubscriptionRecordNotCreated(authProviderId: AuthProviderId, subscriptionJourneyRecord: SubscriptionJourneyRecord) = {
    stubFor(
      post(
        urlEqualTo(s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(authProviderId.id)}"))
        .withRequestBody(equalToJson(
          Json.toJson(subscriptionJourneyRecord).toString(), true, true ))
        .willReturn(aResponse().withStatus(Status.BAD_REQUEST))
    )
  }

  def givenSubscriptionRecordDeleted(authProviderId: AuthProviderId) =
    stubFor(
      delete(
        urlEqualTo(s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(authProviderId.id)}")
      ).willReturn(aResponse().withStatus(Status.NO_CONTENT))
    )

  def givenSubscriptionRecordNotDeleted(authProviderId: AuthProviderId) =
    stubFor(
      delete(
        urlEqualTo(s"/agent-subscription/subscription/journey/primaryId/${encodePathSegment(authProviderId.id)}")
      ).willReturn(aResponse().withStatus(Status.BAD_GATEWAY))
    )

}