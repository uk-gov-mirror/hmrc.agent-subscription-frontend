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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@ImplementedBy(classOf[FrontendAppConfig])
trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val betaFeedbackUrl: String
  val betaFeedbackUnauthenticatedUrl: String
  val governmentGatewayUrl: String
  val blacklistedPostcodes: Set[String]
  val journeyName: String
  val agentServicesAccountUrl: String
  val agentAssuranceBaseUrl: String
  val agentAssuranceRun: Boolean
  val addressLookupContinueUrl: String
  val surveyRedirectUrl: String
  val companyAuthSignInUrl: String
  val chainedSessionDetailsTtl: Int
  val cachableSessionDomain: String
  val sessionCacheBaseUrl: String
  val agentMappingBaseUrl: String
  val addressLookupFrontendBaseUrl: String
  def agentMappingFrontendStartUrl(continueId: String): String
  val ggRegistrationFrontendExternalUrl: String
  val ssoBaseUrl: String
  val rootContinueUrl: String
  val agentSubscriptionBaseUrl: String
  val agentSubscriptionFrontendExternalUrl: String
  def contactFrontendAccessibilityUrl(userAction: String): String
  val timeout: Int
  val timeoutCountdown: Int
  val appName: String
  val languageToggle: Boolean
  val languageMap: Map[String, Lang]
  def routeToSwitchLanguage: String => Call
  val companiesHouseUrl: String
}

@Singleton
class FrontendAppConfig @Inject()(servicesConfig: ServicesConfig) extends AppConfig {

  override val appName = "agent-subscription-frontend"

  def getConf(key: String): String = servicesConfig.getString(key)

  private val servicesAccountUrl = getConf("microservice.services.agent-services-account-frontend.external-url")
  private val servicesAccountPath =
    getConf("microservice.services.agent-services-account-frontend.start.path")

  override val analyticsToken: String = getConf(s"google-analytics.token")
  override val analyticsHost: String = getConf(s"google-analytics.host")

  override val betaFeedbackUrl: String = getConf("betaFeedbackUrl")
  override val betaFeedbackUnauthenticatedUrl: String = getConf("betaFeedbackUnauthenticatedUrl")
  override val reportAProblemPartialUrl: String = getConf("reportAProblemPartialUrl")
  override val reportAProblemNonJSUrl: String = getConf("reportAProblemNonJSUrl")

  override val governmentGatewayUrl: String = getConf("government-gateway.url")
  override val blacklistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override val journeyName: String =
    getConf("microservice.services.address-lookup-frontend.journeyName")
  override val agentServicesAccountUrl: String = s"$servicesAccountUrl$servicesAccountPath"
  override lazy val agentAssuranceBaseUrl = servicesConfig.baseUrl("agent-assurance")
  override val agentAssuranceRun: Boolean = servicesConfig.getBoolean("features.agent-assurance-run")
  override val addressLookupContinueUrl: String =
    getConf("microservice.services.address-lookup-frontend.new-address-callback.url")
  override val surveyRedirectUrl: String = getConf("surveyRedirectUrl")
  override val agentSubscriptionFrontendExternalUrl: String = getConf(
    "microservice.services.agent-subscription-frontend.external-url")
  override val sessionCacheBaseUrl: String = servicesConfig.baseUrl("cachable.session-cache")

  override val companyAuthSignInUrl: String = getConf("microservice.services.companyAuthSignInUrl")
  override val chainedSessionDetailsTtl: Int = servicesConfig.getInt("mongodb.chainedsessiondetails.ttl")
  override val cachableSessionDomain: String =
    getConf("microservice.services.cachable.session-cache.domain")
  override val agentMappingBaseUrl: String = servicesConfig.baseUrl("agent-mapping")
  override def agentMappingFrontendStartUrl(continueId: String): String =
    s"${getConf("microservice.services.agent-mapping-frontend.external-url")}${getConf(
      "microservice.services.agent-mapping-frontend.start.path")}?continueId=$continueId"

  override val addressLookupFrontendBaseUrl: String = servicesConfig.baseUrl("address-lookup-frontend")

  val ssoRedirectUrl: String = "/government-gateway-registration-frontend?accountType=agent&origin=unknown"

  override val agentSubscriptionBaseUrl: String = servicesConfig.baseUrl("agent-subscription")

  override val ssoBaseUrl: String = servicesConfig.baseUrl("sso")

  override val ggRegistrationFrontendExternalUrl: String =
    s"${getConf("microservice.services.government-gateway-registration-frontend.externalUrl")}$ssoRedirectUrl"

  private val returnAfterGGCredsCreatedPath: String = "/agent-subscription/return-after-gg-creds-created"
  override val rootContinueUrl: String = s"$agentSubscriptionFrontendExternalUrl$returnAfterGGCredsCreatedPath"

  def contactFrontendAccessibilityUrl(userAction: String): String =
    s"${getConf(s"microservice.services.contact-frontend.external-url")}" +
      s"/contact/accessibility?service=AOSS&userAction=$userAction"

  override val timeout: Int = servicesConfig.getInt("timeoutDialog.timeout-seconds")
  override val timeoutCountdown: Int = servicesConfig.getInt("timeoutDialog.timeout-countdown-seconds")

  override val languageToggle: Boolean = servicesConfig.getBoolean("features.enable-welsh-toggle")

  override val languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  override def routeToSwitchLanguage =
    (lang: String) => routes.AgentSubscriptionLanguageController.switchToLanguage(lang)

  override val companiesHouseUrl: String = getConf("companies-house.url")

}
