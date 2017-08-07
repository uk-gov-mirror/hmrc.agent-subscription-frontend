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

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

class AddressLookupAddressValidationSpec extends FlatSpec with Matchers {
  private val blacklistedPostCodes: Set[String] = Set("BB1 1BB", "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)


  private val addressLine1 = "12345678901234567890123456789012345"
  private val addressLine2 = ""
  private val addressLine3 = "Ipswich"
  private val addressLine4 = "Ipswich 4"
  private val addressLine5 = "This is a very very very very very very very long address line"
  private val postcode = Some("GT5 7WW")
  private val country = Country("GB",Some("United Kingdom"))
  private val countryCode = "GB"
  private val addressLine1_9kingsRoad = "9 King Road"
  private val postcode_bb11bb = Some("BB1 1BB")
  private val jsValue = (address: AddressLookupFrontendAddress) => Json.parse(
    s"""{
                              		"lines": ["${addressLine(address.lines, 0)}",
                              		          "${addressLine(address.lines, 1)}",
       		                                  "${addressLine(address.lines, 2)}",
       		                                  "${addressLine(address.lines, 3)}"],
                              		"postcode": "${address.postcode.getOrElse("")}",
                              		"country": {
                              			"code": "${address.country.code}",
                              			"name": "United Kingdom"
                              		}

                              }""".stripMargin)

  "Address Validation" should "fail for Empty PostCode (empty string)" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, addressLine2, addressLine3, addressLine4), Some(""), country)
    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
  }

  "Address Validation" should "fail for no postcode (None)" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, addressLine2, addressLine3, addressLine4), None, country)
    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
  }

  // TODO APB-1014 do we need to be converting to/from JSON in most of the tests?
  // val entity = jsValue(address).as[AddressLookupFrontendAddress]

  "Address Validation" should "fail for Blacklisted PostCode" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, addressLine2, addressLine3,
      addressLine4), postcode_bb11bb, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.blacklisted")))
  }

  "Address Validation" should "be Successful for even if 5th address line exists and 5th line is not valid" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, addressLine2, addressLine3,
      addressLine4, addressLine5), postcode, country)

    val validationResult = AddressValidator.validateAddress(address, blacklistedPostCodes)
    validationResult.isValid shouldBe true
    validationResult shouldBe Valid(DesAddress(addressLine1_9kingsRoad, Some(addressLine2), Some(addressLine3),
      Some(addressLine4), postcode, countryCode))
  }

  "Address Validation" should "be Successful for Postcode matching in Regex" in {
    val address = AddressLookupFrontendAddress(Seq( addressLine1_9kingsRoad, addressLine2, addressLine3,
      addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult.isValid shouldBe true
    validationResult shouldBe Valid(DesAddress(addressLine1_9kingsRoad, Some(addressLine2), Some(addressLine3),
      Some(addressLine4), postcode, countryCode))
  }

  "Address Validation" should "pass for address line1 length exactly 35 chars" in {
    val address = AddressLookupFrontendAddress(Seq( addressLine1, addressLine2,
      addressLine3, addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult.isValid shouldBe true
    validationResult shouldBe Valid(DesAddress(addressLine1, Some(addressLine2), Some(addressLine3),
      Some(addressLine4), postcode, countryCode))
  }

  "Address Validation" should "pass when only address line1 is provided" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult.isValid shouldBe true
    validationResult shouldBe Valid(DesAddress(addressLine1, Some(""), Some(""),
      Some(""), postcode, countryCode))
  }

  "Address Validation" should "fail when no lines are provided" in {
    val address = AddressLookupFrontendAddress(Seq.empty, postcode, country)

    val validationResult = AddressValidator.validateAddress(address, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.lines.empty")))
  }

  "Address Validation" should "be Successful when only a few address lines are provided" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, "", "",
      addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult.isValid shouldBe true
    validationResult shouldBe Valid(DesAddress(addressLine1_9kingsRoad, Some(""), Some(""), Some(addressLine4), postcode, countryCode))
  }

  "Address Validation" should "fail for address line1 length greater than 35 characters" in {
    val address = AddressLookupFrontendAddress(Seq("9 King Road 9 King Road 9 King Road 9 King Road", "",
      addressLine3, addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.maxLength", 35, entity.lines(0))))
     s"Length of line ${entity.lines(0)} must be up to 35"
  }

  "Address Validation" should "fail for address line2 length greater than 35 characters" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, "Ipwich line 2 Ipwich line 2 Ipwich line 2",
      addressLine3, addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.maxLength", 35, entity.lines(1))))
  }

  "Address Validation" should "fail for address line2 violating DES regex" in {
    val address = AddressLookupFrontendAddress(Seq(addressLine1_9kingsRoad, "<>'",
      addressLine3, addressLine4), postcode, country)


    val validationResult = AddressValidator.validateAddress(address, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.des.text.invalid.withInput", address.lines(1))))
  }

  "Address Validation" should "fail for address line2 violating DES regex and max length for line1" in {
    val address = AddressLookupFrontendAddress(Seq("9 King Road 9 King Road 9 King Road 9 King Road", "<>'",
      addressLine3, addressLine4), postcode, country)


    val validationResult = AddressValidator.validateAddress(address, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(
      ValidationError("error.address.maxLength", 35, address.lines(0)),
      ValidationError("error.des.text.invalid.withInput", address.lines(1))
    ))
  }

  "Address Validation" should "fail for address line1 and line2 length greater than 35 characters" in {
    val address = AddressLookupFrontendAddress(Seq("9 King Road 9 King Road 9 King Road 9 King Road",
      "Ipwich line 2 Ipwich line 2 Ipwich line 2", addressLine3, addressLine4), postcode, country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.maxLength", 35, entity.lines(0)),
      ValidationError("error.address.maxLength", 35, entity.lines(1))))
  }

  "Address Parallel Validation" should "fail for address line1 and line2 length greater than 35 characters and blacklisted postcode" in {
    val address = AddressLookupFrontendAddress(Seq("9 King Road 9 King Road 9 King Road 9 King Road",
      "Ipwich line 2 Ipwich line 2 Ipwich line 2", addressLine3, addressLine4), Some("DD1 1DD"), country)

    val entity = jsValue(address).as[AddressLookupFrontendAddress]

    val validationResult = AddressValidator.validateAddress(entity, blacklistedPostCodes)
    validationResult shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.maxLength", 35, entity.lines(0)),
      ValidationError("error.address.maxLength", 35, entity.lines(1)),
      ValidationError("error.postcode.blacklisted")))
  }

  private def addressLine(lines: Seq[String], lineToGet: Int): String = {
    if((lineToGet + 1) > lines.length) "" else lines(lineToGet)
  }
}
