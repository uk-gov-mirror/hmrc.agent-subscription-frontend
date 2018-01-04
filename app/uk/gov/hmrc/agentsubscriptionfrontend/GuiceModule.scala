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

package uk.gov.hmrc.agentsubscriptionfrontend

import java.net.URL
import javax.inject.Provider

import com.google.inject.AbstractModule
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names.named
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger, LoggerLike}
import uk.gov.hmrc.agentsubscriptionfrontend.config._
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.http.HttpGet
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector

import scala.reflect.ClassTag

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  trait ConfigPropertyType[A] {
    def bindConfigProperty(clazz: Class[A])(propertyName: String): ScopedBindingBuilder
  }

  object ConfigPropertyType {
    implicit val stringConfigProperty = new ConfigPropertyType[String] {
      def bindConfigProperty(clazz: Class[String])(propertyName: String): ScopedBindingBuilder =
        bind(clazz).annotatedWith(named(s"$propertyName")).toProvider(new StringConfigPropertyProvider(propertyName))

      private class StringConfigPropertyProvider(propertyName: String) extends Provider[String] {
        override lazy val get = getConfString(propertyName, throw new RuntimeException(s"No configuration value found for '$propertyName'"))

        def getConfString(confKey: String, defString: => String) = {
          configuration.getString(s"$env.$confKey").getOrElse(configuration.getString(confKey).getOrElse(defString))
        }
      }

    }

    implicit val intConfigProperty = new ConfigPropertyType[Int] {
      def bindConfigProperty(clazz: Class[Int])(propertyName: String): ScopedBindingBuilder =
        bind(clazz).annotatedWith(named(s"$propertyName")).toProvider(new IntConfigPropertyProvider(propertyName))

      private class IntConfigPropertyProvider(propertyName: String) extends Provider[Int] {
        override lazy val get = getConfInt(propertyName, throw new RuntimeException(s"No configuration value found for '$propertyName'"))

        def getConfInt(confKey: String, defInt: => Int) = {
          configuration.getInt(s"$env.$confKey").getOrElse(configuration.getInt(confKey).getOrElse(defInt))
        }
      }

    }

    implicit val booleanConfigProperty = new ConfigPropertyType[Boolean] {
      def bindConfigProperty(clazz: Class[Boolean])(propertyName: String): ScopedBindingBuilder =
        bind(clazz).annotatedWith(named(s"$propertyName")).toProvider(new BooleanConfigPropertyProvider(propertyName))

      private class BooleanConfigPropertyProvider(propertyName: String) extends Provider[Boolean] {
        override lazy val get = getConfBool(propertyName)

        def getConfBool(confKey: String) = {
          configuration.getBoolean(s"$env.$confKey").getOrElse(configuration.getBoolean(confKey).getOrElse(false))
        }
      }

    }
  }

  object ConfigProperty {
    def bindConfigProperty[A](propertyName: String)(implicit classTag: ClassTag[A], ct: ConfigPropertyType[A]): ScopedBindingBuilder =
      ct.bindConfigProperty(classTag.runtimeClass.asInstanceOf[Class[A]])(propertyName)
  }

  import ConfigProperty._

  override def configure(): Unit = {
    bindConfigProperty[String]("appName")
    bind(classOf[AppConfig]).to(classOf[FrontendAppConfig])
    bind(classOf[AuthConnector]).to(classOf[FrontendAuthConnector])
    bind(classOf[AuditConnector]).to(classOf[FrontendAuditConnector])
    bind(classOf[HttpGet]).to(classOf[HttpVerbs])
    bind(classOf[SessionCache]).to(classOf[AgentSubscriptionSessionCache])
    bind(classOf[SessionStoreService])
    bind(classOf[LoggerLike]).toInstance(Logger)
    bindBaseUrl("agent-assurance")
    bindBaseUrl("agent-subscription")
    bindBaseUrl("government-gateway-authentication")
    bindBaseUrl("address-lookup-frontend")
    bindBaseUrl("sso")
    bindBaseUrl("cachable.session-cache")
    bindBaseUrl("auth")
    bindConfigProperty[String]("surveyRedirectUrl")
    bindConfigProperty[String]("sosRedirectUrl")
    bindConfigProperty[Int]("mongodb.knownfactsresult.ttl")
    bindConfigProperty[Boolean]("agentAssuranceFlag")
    bindServiceProperty("cachable.session-cache.domain")
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL]).annotatedWith(named(s"$serviceName-baseUrl")).toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }

  private def bindServiceProperty(propertyName: String) =
    bind(classOf[String]).annotatedWith(named(propertyName)).toProvider(new BaseServicePropertyProvider(propertyName))

  private class BaseServicePropertyProvider(propertyName: String) extends Provider[String] {
    override lazy val get = getConfString(propertyName, {
      throw new Exception(s"Config property for service not found $propertyName")
    })
  }

}
