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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.data.Form
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.KnownFacts
import uk.gov.hmrc.play.test.UnitSpec

class KnownFactsFormSpec extends UnitSpec {

  "knownFactsForm" should {

    behave like checkKnownFactsForm("sole_trader")(BusinessIdentificationForms.knownFactsForm("sole_trader"))
    behave like checkKnownFactsForm("partnership")(BusinessIdentificationForms.knownFactsForm("partnership"))
    behave like checkKnownFactsForm("limited_company")(BusinessIdentificationForms.knownFactsForm("limited_company"))
    behave like checkKnownFactsForm("llp")(BusinessIdentificationForms.knownFactsForm("llp"))

    def checkKnownFactsForm(businessType: String)(form: => Form[KnownFacts]) = {
      s"accept valid utr and postcode fields and produce valid KnownFacts for $businessType" in {
        val result = form.bind(Map("utr" -> "2000000000", "postcode" -> "BN147BU")).value
        result shouldBe Some(KnownFacts(Utr("2000000000"), "BN147BU"))
      }

      s"fill with valid KnownFacts for $businessType" in {
        val result = form.fill(KnownFacts(Utr("2000000000"), "BN147BU")).data
        result shouldBe Map("utr" -> "2000000000", "postcode" -> "BN147BU")
      }

      s"not produce KnownFacts if utr is missing for $businessType" in {
        val result = form.bind(Map("postcode" -> "BN147BU")).value
        result shouldBe None
      }

      s"not produce KnownFacts if utr is invalid for $businessType" in {
        val result = form.bind(Map("utr" -> "foo", "postcode" -> "BN147BU")).value
        result shouldBe None
      }

      s"not produce KnownFacts if postcode is missing for $businessType" in {
        val result = form.bind(Map("utr" -> "2000000000")).value
        result shouldBe None
      }

      s"not produce KnownFacts if postcode is invalid for $businessType" in {
        val result = form.bind(Map("utr" -> "2000000000", "postcode" -> "foo")).value
        result shouldBe None
      }
    }
  }
}
