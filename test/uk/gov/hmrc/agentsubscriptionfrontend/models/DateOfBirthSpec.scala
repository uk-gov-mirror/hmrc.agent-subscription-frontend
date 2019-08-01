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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import java.time.LocalDate

import play.api.libs.json.{JsError, JsNumber, JsResultException, JsString, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DateOfBirthSpec extends UnitSpec {

  "DateOfBirth" should {
    "serialize to json string" in {
      Json.toJson(DateOfBirth(LocalDate.parse("2019-01-01"))) shouldBe JsString("2019-01-01")
    }

    "successfully deserialize from json string" in {
      JsString("2019-01-01").as[DateOfBirth] shouldBe DateOfBirth(LocalDate.parse("2019-01-01"))
    }

    "return an error when the date cannot be parsed" in {
      intercept[JsResultException] {
        JsString("20190101").as[DateOfBirth]
      }.getMessage should include("Could not parse date as yyyy-MM-dd")
    }

    "return an error when the json is not a JsString" in {
      intercept[JsResultException] {
        JsNumber(20190101).as[DateOfBirth]
      }.getMessage should include("Expected string but got 20190101")
    }
  }

}
