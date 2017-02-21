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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import javax.inject.Singleton

import play.api.Logger
import play.api.Play.{configuration, current}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
}

trait StrictConfig{
  def loadConfig(key: String): String = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))
}

object GGConfig extends StrictConfig {
  lazy val ggSignInUrl: String = {
    val ggBaseUrl = loadConfig("authentication.government-gateway.sign-in.base-url")
    Logger.debug(s"ggBaseUrl = $ggBaseUrl")
    val ggSignInPath = loadConfig("authentication.government-gateway.sign-in.path")
    val url = s"$ggBaseUrl$ggSignInPath"
    Logger.debug(s"ggSignInUrl = $url")
    url
  }

  lazy val checkAgencyStatusCallbackUrl: String = loadConfig("authentication.login-callback.url") +
    routes.CheckAgencyController.showCheckAgencyStatus().url
}

@Singleton
class FrontendAppConfig extends AppConfig with StrictConfig with ServicesConfig {
  private lazy val contactHost = runModeConfiguration.getString(s"contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "AOSS"

  override lazy val analyticsToken: String = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost: String = loadConfig(s"google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
}
