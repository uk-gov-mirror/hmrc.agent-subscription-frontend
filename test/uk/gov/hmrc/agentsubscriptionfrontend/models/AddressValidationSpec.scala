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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

class AddressValidationSpec extends FlatSpec with Matchers {
  lazy val log = LoggerFactory.getLogger(classOf[AddressValidationSpec])

  private val blacklistedPostCodes: Set[String] = Set("BB1 1BB", "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

  private val jsValue = (value: String) => Json.parse(
    s"""{
                              	"auditRef": "093b7e77-81c4-4663-a580-fa9383775a24",
                              	"address": {
                              		"lines": ["9 King Road", "", "Ipswich", "Ipswich 4"],
                              		"postcode": "$value",
                              		"country": {
                              			"code": "GB",
                              			"name": "United Kingdom"
                              		}
                              	}
                              }""".stripMargin)



  "Address Validation" should "fail for Empty PostCode" in {
    val postcode = ""
    val entity = jsValue(postcode).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(s"Postcode is empty"))
  }

  "Address Validation" should "fail for Blacklisted PostCode" in {
    val postcode = "BB1 1BB"
    val entity = jsValue(postcode).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set("This postcode is blocked and cannot be used"))
  }

  "Address Validation" should "be Successful for Postcode matching in Regex" in {
    val postcode = "GT5 7WW"
    val entity = jsValue(postcode).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Valid(())
  }
}