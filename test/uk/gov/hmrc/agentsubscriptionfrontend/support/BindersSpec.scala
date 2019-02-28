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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models.IdentifyBusinessType
import uk.gov.hmrc.play.test.UnitSpec

class BindersSpec extends UnitSpec {

  "Binders.businessTypeBinder.bind" should {
    "successful NORMAL binding" when {
      "bind correctly" in {
        val soleTrader = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("sole_trader")))
        soleTrader.get shouldBe Right(IdentifyBusinessType.SoleTrader)

        val limitedCo = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("limited_company")))
        limitedCo.get shouldBe Right(IdentifyBusinessType.LimitedCompany)

        val partnership = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("partnership")))
        partnership.get shouldBe Right(IdentifyBusinessType.Partnership)

        val llpBind = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("llp")))
        llpBind.get shouldBe Right(IdentifyBusinessType.Llp)
      }

      "successful BAD binding" when {
        "IdentifyBusinessType.Invalid due to invalid input => Left => BadRequest" in {
          val undefinedEmpty = Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("")))
          undefinedEmpty.get shouldBe Left("Submitted businessType value was invalid")

          val undefinedBadInput =
            Binders.businessTypeBinder.bind("businessType", Map("businessType" -> Seq("someInvalidType")))
          undefinedBadInput.get shouldBe Left("Submitted businessType value was invalid")

          val undefinedNothing = Binders.businessTypeBinder.bind("businessType", Map.empty)
          undefinedNothing shouldBe None
        }
      }
    }
  }

  "Binders.businessTypeBinder.bind" should {
    "unbind" in {
      Binders.businessTypeBinder
        .unbind("businessType", IdentifyBusinessType.SoleTrader) shouldBe "businessType=sole_trader"
      Binders.businessTypeBinder
        .unbind("businessType", IdentifyBusinessType.LimitedCompany) shouldBe "businessType=limited_company"
      Binders.businessTypeBinder
        .unbind("businessType", IdentifyBusinessType.Partnership) shouldBe "businessType=partnership"
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.Llp) shouldBe "businessType=llp"

      //Undefined "businessType=invalid" shouldBe filtered and rejected
      Binders.businessTypeBinder.unbind("businessType", IdentifyBusinessType.Invalid) shouldBe "businessType=invalid"
    }
  }
}
