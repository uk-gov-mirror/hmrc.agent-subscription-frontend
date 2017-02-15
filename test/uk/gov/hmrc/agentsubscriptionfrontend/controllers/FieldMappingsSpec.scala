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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.scalatest.EitherValues
import play.api.data.FormError
import uk.gov.hmrc.play.test.UnitSpec

class FieldMappingsSpec extends UnitSpec with EitherValues {

  "utr bind" should {
    val utrMapping = FieldMappings.utr.withPrefix("testKey")

    def bind(fieldValue: String) = utrMapping.bind(Map("testKey" -> fieldValue))

    "accept valid UTRs" in {
      bind("1234567890") shouldBe Right("1234567890")
    }

    "give \"error.required\" error when it is not supplied" in {
      utrMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.required\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.required\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.utr.invalid\" error" when {
      "it has more than 10 digits" in {
        bind("12345678901") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "it has fewer than 10 digits" in {
        bind("123456789") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "it has non-digit characters" in {
        bind("123456789Z") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }
    }
  }
}
