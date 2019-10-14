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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import java.util.Collections.emptyList

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.play.config.ServicesConfig

import scala.collection.JavaConverters._

trait AppConfig {
  val environment: Environment
  val configuration: Configuration
  val isDevMode: Boolean
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
  val domainWhiteList: Set[String]
  val agentAssuranceRun: Boolean
  val addressLookupContinueUrl: String
  val surveyRedirectUrl: String
  val companyAuthSignInUrl: String
  val chainedSessionDetailsTtl: Int
  val cacheableSessionDomain: String
  def agentMappingFrontendStartUrl(continueId: String): String
  val ggRegistrationFrontendExternalUrl: String
  val rootContinueUrl: String
  def contactFrontendAccessibilityUrl(userAction: String): String
}

@Singleton
class FrontendAppConfig @Inject()(val environment: Environment, val configuration: Configuration)
    extends AppConfig with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override protected def mode = environment.mode

  override val isDevMode: Boolean = env == Mode.Dev.toString

  private val servicesAccountUrl = getServicesConfStringOrFail("agent-services-account-frontend.external-url")
  private val servicesAccountPath = getServicesConfStringOrFail("agent-services-account-frontend.start.path")

  override val analyticsToken: String = getConfStringOrFail(s"google-analytics.token")
  override val analyticsHost: String = getConfStringOrFail(s"google-analytics.host")

  override val betaFeedbackUrl: String = getConfStringOrFail("betaFeedbackUrl")
  override val betaFeedbackUnauthenticatedUrl: String = getConfStringOrFail("betaFeedbackUnauthenticatedUrl")
  override val reportAProblemPartialUrl: String = getConfStringOrFail("reportAProblemPartialUrl")
  override val reportAProblemNonJSUrl: String = getConfStringOrFail("reportAProblemNonJSUrl")

  override val governmentGatewayUrl: String = getConfStringOrFail("government-gateway.url")
  override val blacklistedPostcodes: Set[String] =
    PostcodesLoader.load("/po_box_postcodes_abp_49.csv").map(x => x.toUpperCase.replace(" ", "")).toSet
  override val journeyName: String = getServicesConfStringOrFail("address-lookup-frontend.journeyName")
  override val agentServicesAccountUrl: String = s"$servicesAccountUrl$servicesAccountPath"
  override val domainWhiteList: Set[String] =
    runModeConfiguration.getStringList("continueUrl.domainWhiteList").getOrElse(emptyList()).asScala.toSet
  override val agentAssuranceRun: Boolean = getConfBooleanOrFail("features.agent-assurance-run")
  override val addressLookupContinueUrl: String = getServicesConfStringOrFail(
    "address-lookup-frontend.new-address-callback.url")
  override val surveyRedirectUrl: String = getConfStringOrFail(s"$env.surveyRedirectUrl")

  override val companyAuthSignInUrl: String = getConfStringOrFail(s"$env.companyAuthSignInUrl")
  override val chainedSessionDetailsTtl: Int = getConfIntOrFail(s"$env.mongodb.chainedsessiondetails.ttl")
  override val cacheableSessionDomain: String = getServicesConfStringOrFail("cachable.session-cache.domain")
  override def agentMappingFrontendStartUrl(continueId: String): String =
    s"${getServicesConfStringOrFail("agent-mapping-frontend.external-url")}${getServicesConfStringOrFail(
      "agent-mapping-frontend.start.path")}?continueId=$continueId"

  val ssoRedirectUrl: String = "/government-gateway-registration-frontend?accountType=agent&origin=unknown"

  override val ggRegistrationFrontendExternalUrl: String =
    s"${getConfStringOrFail(s"$env.microservice.services.government-gateway-registration-frontend.externalUrl")}$ssoRedirectUrl"

  private val returnAfterGGCredsCreatedPath: String = "/agent-subscription/return-after-gg-creds-created"

  override val rootContinueUrl: String =
    if (isDevMode) s"http://localhost:9437$returnAfterGGCredsCreatedPath" else returnAfterGGCredsCreatedPath

  def contactFrontendAccessibilityUrl(userAction: String): String =
    s"${getConfStringOrFail(s"$env.microservice.services.contact-frontend.external-url")}" +
      s"/contact/accessibility?service=agent-subscription-frontend&userAction=$userAction"

  def getServicesConfStringOrFail(key: String): String =
    getConfString(key, throw new Exception(s"Property not found $key"))
  def getConfStringOrFail(key: String): String =
    configuration.getString(key).getOrElse(throw new Exception(s"Property not found $key"))
  def getConfBooleanOrFail(key: String): Boolean =
    configuration.getBoolean(key).getOrElse(throw new Exception(s"Property not found $key"))
  def getConfIntOrFail(key: String): Int =
    configuration.getInt(key).getOrElse(throw new Exception(s"Property not found $key"))
}
