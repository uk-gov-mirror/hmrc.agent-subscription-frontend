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

package uk.gov.hmrc.agentsubscriptionfrontend.views

import play.api.data.{Form, Forms}
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestMessagesApi
import uk.gov.hmrc.play.test.UnitSpec

class FormErrorSummarySpec extends UnitSpec {

  private case class Model(name: String)

  private val maxLength = 9

  private val testForm = Form[Model](mapping("name" -> text(maxLength = maxLength))(Model.apply)(Model.unapply))

  private implicit val messages = Messages(Lang("en"), TestMessagesApi.testMessagesApi)

  "form_error_summary" should {
    "display error messages including arguments" in {

      val formWithError = testForm.bind(Map("name" -> "too long too long"))
      uk.gov.hmrc.play.views.html.helpers.errorSummary("heading", formWithError).toString should include(
        htmlEscapedMessage("error.maxLength", maxLength))
    }
  }

  protected def htmlEscapedMessage(key: String, args: Any*): String =
    HtmlFormat.escape(Messages(key, args: _*)).toString
}
