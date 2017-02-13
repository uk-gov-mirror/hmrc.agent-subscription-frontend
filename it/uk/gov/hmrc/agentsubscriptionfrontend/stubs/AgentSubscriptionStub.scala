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

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

object AgentSubscriptionStub {

  def withMatchingUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(200)))
  }

  def withNonMatchingUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(404)))
  }

  def withErrorForUtrAndPostcode(utr: String, postcode: String): Unit = {
    stubFor(get(urlEqualTo(s"/agent-subscription/registration/${encodePathSegment(utr)}/postcode/${encodePathSegment(postcode)}"))
      .willReturn(
        aResponse()
          .withStatus(500)))
  }
}
