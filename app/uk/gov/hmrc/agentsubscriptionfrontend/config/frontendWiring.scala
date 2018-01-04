/*
 * Copyright 2018 HM Revenue & Customs
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

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import uk.gov.hmrc.http._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.play.http.ws._

@Singleton
class FrontendAuditConnector @Inject()(@Named("appName") val appName: String) extends AuditConnector {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

@Singleton
class FrontendAuthConnector @Inject()(val http: HttpGet, @Named("auth-baseUrl") val baseUrl: URL) extends AuthConnector {
  lazy val serviceUrl = baseUrl.toExternalForm
}

@Singleton
class AgentSubscriptionSessionCache @Inject()(val http: HttpGet with HttpPut with HttpDelete,
                                              @Named("appName") val appName: String,
                                              @Named("cachable.session-cache-baseUrl") val baseUrl: URL,
                                              @Named("cachable.session-cache.domain") val domain: String
                                             ) extends SessionCache {
  override lazy val defaultSource = appName
  override lazy val baseUri = baseUrl.toExternalForm
}

@Singleton
class HttpVerbs @Inject()(val auditConnector: AuditConnector, @Named("appName") val appName: String)
  extends HttpGet with HttpPost with HttpPut with HttpPatch with HttpDelete with WSHttp
    with HttpAuditing {
  override val hooks = Seq(AuditingHook)
}