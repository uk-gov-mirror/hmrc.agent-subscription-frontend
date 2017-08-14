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

import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.play.config.ServicesConfig

object TestAppConfig extends AppConfig with ServicesConfig {

  override val analyticsToken: String = "N/A"
  override val analyticsHost: String = "auto"

  private val contactHost = "http://localhost:9250"
  private val contactFormServiceIdentifier = "AOSS"
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override lazy val betaFeedbackUrl = s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier"
  override lazy val betaFeedbackUnauthenticatedUrl = s"$contactHost/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"
  override lazy val governmentGatewayUrl: String = "http://www.ref.gateway.gov.uk/"
  override lazy val journeyName: String = "agents-subscr"
  override lazy val agentServicesAccountUrl: String = "http://localhost:9401/agent-services-account"

  override lazy val blacklistedPostcodes: Set[String] = Set(
    "AB10 1DU",
    "AB10 1GS",
    "AB10 1TW",
    "AB10 1WS",
    "AB10 1YR",
    "AB10 1ZG",
    "AB101ZTT",
    "AB10 1ZX",
    "AB10 6YH",
    "AB10 7YA",
    "AB11 5YD",
    "AB11 5YL",
    "AB11 5ZH",
    "AB11 6NW"
  )
}
