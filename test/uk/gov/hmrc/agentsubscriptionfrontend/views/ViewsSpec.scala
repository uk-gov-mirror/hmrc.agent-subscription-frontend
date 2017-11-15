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

package uk.gov.hmrc.agentsubscriptionfrontend.views

import org.scalatestplus.play.MixedPlaySpec
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.error_template_Scope0.error_template
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.main_template_Scope0.main_template
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._

class ViewsSpec extends MixedPlaySpec {

  implicit val appConfig = new AppConfig() {
    override val analyticsToken: String = "analyticsToken"
    override val analyticsHost: String = "analyticsHost"
    override val reportAProblemPartialUrl: String = "reportAProblemPartialUrl"
    override val reportAProblemNonJSUrl: String = "reportAProblemNonJSUrl"
    override val betaFeedbackUrl: String = "betaFeedbackUrl"
    override val betaFeedbackUnauthenticatedUrl: String = "betaFeedbackUnauthenticatedUrl"
    override val governmentGatewayUrl: String = "governmentGatewayUrl"
    override val blacklistedPostcodes: Set[String] = Set("blacklistedPostcodes")
    override val journeyName: String = "journeyName"
    override val agentServicesAccountUrl: String = "http://localhost:9401/agent-services-account"
    override val domainWhiteList: Set[String] = Set("www.foo.com", "foo.org")
    override val agentAssuranceFlag: Boolean = false
  }

  "error_template view" should {

    "render title, heading and message" in new App {
      val view = new error_template()
      val html = view.render(
        "My custom page title", "My custom heading", "My custom message", FakeRequest(), applicationMessages, appConfig)

      contentAsString(html) must {
        include("My custom page title") and
          include("My custom heading") and
          include("My custom message")
      }

      val hmtl2 = view.f("My custom page title", "My custom heading", "My custom message")(
        FakeRequest(), applicationMessages, appConfig
      )
      hmtl2 must be(html)
    }
  }

  "main_template view" should {

    "render title, header, sidebar and main content" in new App {
      val view = new main_template()
      val html = view.render(
        appConfig = appConfig,
        title = "My custom page title",
        sidebarLinks = Some(Html("sidebarLinks")),
        contentHeader = Some(Html("contentHeader")),
        bodyClasses = Some("bodyClasses"),
        mainClass = Some("mainClass"),
        scriptElem = Some(Html("<script src=\"@controllers.routes.Assets.at(\"javascripts/scripts.js\")\" type=\"text/javascript\"></script>")),
        userIsLoggedIn = true,
        mainContent = Html("mainContent"),
        request = FakeRequest(),
        messages = applicationMessages
      )

      contentAsString(html) must {
        include("My custom page title") and
          include("sidebarLinks") and
          include("contentHeader") and
          include("type=\"text/javascript\"") and
          include("mainContent") and
          include("bodyClasses") and
          include("mainClass")
      }

      val hmtl2 = view.f(
        appConfig,
        "My custom page title",
        Some(Html("sidebarLinks")),
        Some(Html("contentHeader")),
        Some("bodyClasses"),
        Some("mainClass"),
        Some(Html("scriptElem")),
        true
      )(Html("mainContent"))(FakeRequest(), applicationMessages)
      hmtl2 must be(html)
    }

  }

}
