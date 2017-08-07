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
import cats.data.{NonEmptyList, Validated}
import play.api.data.validation.ValidationError
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.AddressValidator._
import uk.gov.hmrc.agentsubscriptionfrontend.support.testAddressLookupFrontendAddress
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AddressValidatorSpec extends UnitSpec {

  private val validLine = "12345678901234567890123456789012345"
  private val validLine2 = "22345678901234567890123456789012345"
  private val validLine3 = "32345678901234567890123456789012345"
  private val validLine4 = "42345678901234567890123456789012345"

  private val tooLongLine = "123456789012345678901234567890123456"
  private val errorsForTooLongLine = NonEmptyList.of(ValidationError("error.address.maxLength", 35, tooLongLine))

  private val nonMatchingLine = "<"
  private val errorsForNonMatchingLine = NonEmptyList.of(ValidationError("error.des.text.invalid.withInput", nonMatchingLine))

  private val tooLongAndNonMatchingLine = "123456789012345678901234567890123456<"
  private val errorsForTooLongAndNonMatchingLine = NonEmptyList.of(
    ValidationError("error.address.maxLength", 35, tooLongAndNonMatchingLine),
    ValidationError("error.des.text.invalid.withInput", tooLongAndNonMatchingLine)
  )

  private val validPostcode = "AA1 1AA"

  private val blacklistedPostcodes: Set[String] = Set("BB1 1BB", "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

  "validateLine" should {
    "return the validated line if it is valid" in {
      validateLine(validLine) shouldBe Valid(validLine)
    }

    "return an error if the line is too long for DES" in {
      validateLine(tooLongLine) shouldBe Invalid(errorsForTooLongLine)
    }

    "return an error if the line does not match the DES regex" in {
      validateLine(nonMatchingLine) shouldBe Invalid(errorsForNonMatchingLine)
    }

    "accumulate errors if there are multiple validation problems" in {
      validateLine(tooLongAndNonMatchingLine) shouldBe Invalid(errorsForTooLongAndNonMatchingLine)
    }
  }

  "validatePostcode" should {
    "return the validated postcode if it is valid" in {
      validatePostcode(Some(validPostcode), blacklistedPostcodes) shouldBe Valid(Some(validPostcode))
    }

    "return an error if format is invalid" in {
      validatePostcode(Some("not a postcode"), blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.invalid")))
    }

    "return an error if format is invalid but contains a valid postcode" in {
      validatePostcode(Some(s"not a postcode $validPostcode not a postcode"), blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.invalid")))
    }

    "return an error if there is no postcode (None)" in {
      validatePostcode(None, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
    }

    "return an error if the postcode is empty" in {
      validatePostcode(Some(""), blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
    }

    "return an error if the postcode is blacklisted regardless of spacing" in {
      validatePostcode(Some("BB11BB"), blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.blacklisted")))
    }
  }

  "validateAddress" should {
    "populate fields when the input address is valid" in {
      val countryCode = "GB"
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(validLine, validLine2, validLine3, validLine4),
        postcode = Some(validPostcode),
        country = Country(countryCode,Some("United Kingdom"))
      )
      val validatedAddress: Validated[NonEmptyList[ValidationError], DesAddress] = validateAddress(addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe Some(validLine2)
      desAddress.addressLine3 shouldBe Some(validLine3)
      desAddress.addressLine4 shouldBe Some(validLine4)
      desAddress.postcode shouldBe Some(validPostcode)
      desAddress.countryCode shouldBe countryCode
    }

    "not throw an error when there is only one line" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine))
      val validatedAddress: Validated[NonEmptyList[ValidationError], DesAddress] = validateAddress(addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe None
      desAddress.addressLine3 shouldBe None
      desAddress.addressLine4 shouldBe None
    }

    "validate that address line 1 is valid, given it is present" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(tooLongAndNonMatchingLine))
      validateAddress(addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(errorsForTooLongAndNonMatchingLine)
    }

    "validate that address line 1 is present" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq())
      validateAddress(addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.lines.empty")))
    }

    "validate that postcode is valid" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some("not a valid postcode"))
      validateAddress(addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.invalid")))
    }

    "pass on the postcode blacklist so that postcode blacklisting works" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some("BB11BB"))
      validateAddress(addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.blacklisted")))
    }

    "accumulate errors for all fields" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(
        tooLongAndNonMatchingLine,
        nonMatchingLine,
        tooLongLine
      ))
      val validatedAddress: Validated[NonEmptyList[ValidationError], DesAddress] = validateAddress(addressLookupFrontendAddress, blacklistedPostcodes)
      validatedAddress shouldBe Invalid(errorsForTooLongAndNonMatchingLine concat errorsForNonMatchingLine concat errorsForTooLongLine)
    }
  }

  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
