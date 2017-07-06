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
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

class AddressValidationSpec extends FlatSpec with Matchers {
  private val blacklistedPostCodes: Set[String] = Set("BB1 1BB", "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

  private val jsValue = (address: Address) => Json.parse(
    s"""{
                              	"auditRef": "093b7e77-81c4-4663-a580-fa9383775a24",
                              	"address": {
                              		"lines": ["${address.addressLine1}", "${address.addressLine2.getOrElse("")}",
       		"${address.addressLine3.getOrElse("")}", "${address.addressLine4.getOrElse("")}"],
                              		"postcode": "${address.postcode.getOrElse("")}",
                              		"country": {
                              			"code": "${address.countryCode}",
                              			"name": "United Kingdom"
                              		}
                              	}
                              }""".stripMargin)


  "Address Validation" should "fail for Empty PostCode" in {
    val address = Address("9 King Road", Some(""), Some("Ipswich"),
      Some("Ipswich 4"), Some(""), "GB")
    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.postcode.empty")))
  }

  "Address Validation" should "fail for Blacklisted PostCode" in {
    val address = Address("9 King Road", Some(""), Some("Ipswich"),
      Some("Ipswich 4"), Some("BB1 1BB"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.postcode.blacklisted")))
  }

  "Address Validation" should "be Successful for Postcode matching in Regex" in {
    val address = Address("9 King Road", Some(""), Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Valid(())
  }

  "Address Validation" should "pass for address line1 length exactly 35 chars" in {
    val address = Address("12345678901234567890123456789012345", Some(""),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Valid(())
  }

  "Address Validation" should "fail for address line1 length greater than 35 characters" in {
    val address = Address("9 King Road 9 King Road 9 King Road 9 King Road", Some(""),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.address.maxLength", 35, entity.addressLine1)))
    // s"Length of line ${entity.addressLine1} must be up to 35"
  }

  "Address Validation" should "fail for address line2 length greater than 35 characters" in {
    val address = Address("9 King Road ", Some("Ipwich line 2 Ipwich line 2 Ipwich line 2"),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.address.maxLength", 35, entity.addressLine2.get)))
  }

  "Address Validation" should "fail for address line2 violating DES regex" in {
    val address = Address("9 King Road ", Some("<>'"),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")


    val validationResult = Address.validate(address, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.des.text.invalid.withInput", address.addressLine2.get)))
  }

  "Address Validation" should "fail for address line2 violating DES regex and max length for line1" in {
    val address = Address("9 King Road 9 King Road 9 King Road 9 King Road", Some("<>'"),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")


    val validationResult = Address.validate(address, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.des.text.invalid.withInput", address.addressLine2.get),
      ValidationError("error.address.maxLength", 35, address.addressLine1)))
  }

  "Address Validation" should "fail for address line1 and line2 length greater than 35 characters" in {
    val address = Address("9 King Road 9 King Road 9 King Road 9 King Road", Some("Ipwich line 2 Ipwich line 2 Ipwich line 2"),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("GT5 7WW"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.address.maxLength", 35, entity.addressLine1),
      ValidationError("error.address.maxLength", 35, entity.addressLine2.get)))
  }

  "Address Parallel Validation" should "fail for address line1 and line2 length greater than 35 characters and blacklisted postcode" in {
    val address = Address("9 King Road 9 King Road 9 King Road 9 King Road", Some("Ipwich line 2 Ipwich line 2 Ipwich line 2"),
      Some("Ipswich"),
      Some("Ipswich 4"), Some("DD1 1DD"), "GB")

    val entity = jsValue(address).as[Address]

    val validationResult = Address.validate(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(Set(ValidationError("error.address.maxLength", 35, entity.addressLine1),
      ValidationError("error.address.maxLength", 35, entity.addressLine2.get),
      ValidationError("error.postcode.blacklisted")))
  }
}