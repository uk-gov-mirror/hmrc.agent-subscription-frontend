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

case class SampleUser(authJson: String, userDetailsJson: String) {
  private val json: JsValue = Json.parse(authJson)

  val authorityUri: String = (json \ "uri").as[String]
  val userDetailsLink: String = (json \ "userDetailsLink").as[String]
  val enrolmentsLink: String = (json \ "enrolments").as[String]
}

object SampleUsers {

  private val subscribingAgentOid = "1234567890"
  private val subscribingAgentUserDetailsLink: String = s"/user-details/id/$subscribingAgentOid"
  def subscribingAgent(implicit wireMockBaseUrl: WireMockBaseUrl) = SampleUser(
      s"""
       |{
       |  "uri": "/auth/oid/$subscribingAgentOid",
       |  "userDetailsLink": "$subscribingAgentUserDetailsLink",
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
    """.stripMargin,
    userDetailsJson = s"""
       |{
       |  "affinityGroup": "Agent",
       |  "authProviderId" : "12345-credId",
       |  "authProviderType" : "GovernmentGateway"
       |}
    """.stripMargin
  )

  private val subscribingAgentOid2 = "1234567899"
  private val subscribingAgentUserDetailsLink2: String = s"/user-details/id/$subscribingAgentOid2"
  def subscribingAgent2(implicit wireMockBaseUrl: WireMockBaseUrl) = SampleUser(
    s"""
       |{
       |  "uri": "/auth/oid/$subscribingAgentOid2",
       |  "userDetailsLink": "$subscribingAgentUserDetailsLink2",
       |  "loggedInAt": "2015-01-19T11:10:33.921Z",
       |  "credentials": {
       |    "gatewayId": "cred-id-12340",
       |    "idaPids": []
       |  },
       |  "accounts": {
       |    "version": 1
       |  },
       |  "lastUpdated": "2015-01-19T11:11:33.923Z",
       |  "credentialStrength": "weak",
       |  "confidenceLevel": 50,
       |  "enrolments": "/auth/oid/$subscribingAgentOid2/enrolments",
       |  "legacyOid": "$subscribingAgentOid2"
       |}
    """.stripMargin,
    userDetailsJson = s"""
                         |{
                         |  "affinityGroup": "Agent",
                         |  "authProviderId" : "12345-credId",
                         |  "authProviderType" : "GovernmentGateway"
                         |}
    """.stripMargin
  )

  private val individualOid = "234567891"
  private val individualUserDetailsLink: String = s"/user-details/id/$individualOid"
  def individual(implicit wireMockBaseUrl: WireMockBaseUrl) = SampleUser(
    s"""
       |{
       |  "uri": "/auth/oid/$individualOid",
       |  "userDetailsLink": "$individualUserDetailsLink",
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
       |  "enrolments": "/auth/oid/$individualOid/enrolments",
       |  "legacyOid": "$individualOid"
       |}
    """.stripMargin,
    userDetailsJson = s"""
       |{
       |  "affinityGroup": "Individual",
       |  "authProviderId" : "12345-credId",
       |  "authProviderType" : "GovernmentGateway"
       |}
    """.stripMargin

  )
}

