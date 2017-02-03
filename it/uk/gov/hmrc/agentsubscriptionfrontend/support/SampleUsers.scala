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

import play.api.libs.json.{JsValue, Json}

case class SampleUser(authJson: String) {
  private val json: JsValue = Json.parse(authJson)

  val authorityUri: String = (json \ "uri").as[String]
}

object SampleUsers {

  private val subscribingAgentOid = "1234567890"
  private val subscribingAgentUserDetailsLink: String = "/user-details/id/2234567890"
  def subscribingAgent(implicit wireMockBaseUrl: WireMockBaseUrl) = SampleUser(
    s"""
       |{
       |  "uri": "/auth/oid/$subscribingAgentOid",
       |  "userDetailsLink": "${wireMockBaseUrl.value}$subscribingAgentUserDetailsLink",
       |  "loggedInAt": "2015-01-19T11:11:34.926Z",
       |  "credentials": {
       |    "gatewayId": "cred-id-12345",
       |    "idaPids": []
       |  },
       |  "accounts": {
       |    "version": 1
       |  },
       |  "lastUpdated": "2015-01-19T11:11:34.926Z",
       |  "credentialStrength": "weak",
       |  "confidenceLevel": 50,
       |  "enrolments": "/auth/oid/$subscribingAgentOid/enrolments",
       |  "legacyOid": "$subscribingAgentOid"
       |}
    """.stripMargin
  )
}
