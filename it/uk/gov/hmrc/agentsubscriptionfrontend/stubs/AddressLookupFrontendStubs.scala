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
import play.api.http.HeaderNames
import play.api.libs.json.Json
import uk.gov.hmrc.agentsubscriptionfrontend.models.Address

trait AddressLookupFrontendStubs {

  def givenAddressLookupInit(journeyId: String, callbackUrl: String): Unit = {
    stubFor(post(urlEqualTo(s"/api/init/$journeyId"))
      .willReturn(
        aResponse()
          .withStatus(202)
          .withHeader(HeaderNames.LOCATION, callbackUrl)
      )
    )
  }

  def givenAddressLookupJourneySucceeded(addressId: String): Unit = {
    stubFor(get(urlEqualTo("/api/dummy/start-journey"))
      .willReturn(
        aResponse()
          .withStatus(302)
          .withHeader(HeaderNames.LOCATION, s"/get-address/$addressId")
      )
    )
  }

  def givenAddressLookupReturnsAddress(addressId: String, town: String = "Sometown", county: String = "County", postcode: String = "AA1 1AA"): Unit = {
    stubFor(get(urlEqualTo(s"/api/confirmed?id=$addressId"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
              |{
              |"address":{
              |    "lines" : [
              |        "1 Some Street"
              |    ],
              |    "town" : "$town",
              |    "county" : "$county",
              |    "postcode" : "$postcode",
              |    "subdivision" :
              |    {
              |        "code" : "GB-ENG",
              |        "name" : "England"
              |    },
              |    "country" :
              |    {
              |        "code" : "GB",
              |        "name" : "United Kingdom"
              |    }
              |}
              |}
              |""".stripMargin
          )
      )
    )
  }

}
