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

package uk.gov.hmrc.agentsubscriptionfrontend.views

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
import uk.gov.hmrc.play.test.UnitSpec

class ViewsSpec extends UnitSpec with GuiceOneAppPerSuite {

  "error_template view" should {

    "render title, heading and message" in new App {
      val appConfig = app.injector.instanceOf[AppConfig]
      val messages = app.injector.instanceOf[Messages]

      val view = app.injector.instanceOf[error_template]
      val html = view
        .render("My custom page title", "My custom heading", "My custom message", FakeRequest(), messages, appConfig)

      contentAsString(html) should {
        include("My custom page title") and
          include("My custom heading") and
          include("My custom message")
      }

      val hmtl2 =
        view.f("My custom page title", "My custom heading", "My custom message")(FakeRequest(), messages, appConfig)
      hmtl2 shouldBe (html)
    }
  }

  "main_template view" should {

    "render title, header, sidebar and main content" in new App {
      val view = app.injector.instanceOf[main_template]
      val appConfig = app.injector.instanceOf[AppConfig]
      val messages = app.injector.instanceOf[Messages]
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
        messages = messages,
        hasTimeout = true
      )

      contentAsString(html) should {
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
        Some(Html("<script src=\"@controllers.routes.Assets.at(\"javascripts/scripts.js\")\" type=\"text/javascript\"></script>")),
        true,
        true
      )(Html("mainContent"))(FakeRequest(), messages)
      hmtl2 shouldBe (html)
    }

  }

}
