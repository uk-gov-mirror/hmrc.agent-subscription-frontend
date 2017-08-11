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
import org.mockito.Mockito.{verify, when}
import org.slf4j.Logger
import play.api.LoggerLike
import play.api.data.validation.ValidationError
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.support.{ResettingMockitoSugar, testAddressLookupFrontendAddress, testCountry}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AddressValidatorSpec extends UnitSpec with ResettingMockitoSugar {

  // Each of these valid lines should be the maximum allowed length, 35
  // chars, to ensure we test the edge case of validation passing when all
  // lines are the maximum allowed length
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
  private val errorsForInvalidPostcode = NonEmptyList.of(ValidationError("error.postcode.invalid"))

  private val blacklistedPostcode = "BB1 1BB"
  private val errorsForBlacklistedPostcode = NonEmptyList.of(ValidationError("error.postcode.blacklisted"))
  private val blacklistedPostcodes: Set[String] = Set(blacklistedPostcode, "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

  private val validCountryCode = "GB"
  val utrTest = Utr("1234567")
  val slf4jLogger = resettingMock[Logger]
  val logger = new LoggerLike {
    override val logger: Logger = slf4jLogger
  }
  val addressValidator = new AddressValidator(logger)
  "validateLine" should {
    "return the validated line if it is valid" in {
      addressValidator.validateLine(validLine) shouldBe Valid(validLine)
    }

    "return an error if the line is too long for DES" in {
      addressValidator.validateLine(tooLongLine) shouldBe Invalid(errorsForTooLongLine)
    }

    "return an error if the line does not match the DES regex" in {
      addressValidator.validateLine(nonMatchingLine) shouldBe Invalid(errorsForNonMatchingLine)
    }

    "accumulate errors if there are multiple validation problems" in {
      addressValidator.validateLine(tooLongAndNonMatchingLine) shouldBe Invalid(errorsForTooLongAndNonMatchingLine)
    }
  }

  "validatePostcode" should {
    "return the validated postcode if it is valid" in {
      addressValidator.validatePostcode(Some(validPostcode), blacklistedPostcodes) shouldBe Valid(Some(validPostcode))
    }

    "return an error if format is invalid" in {
      addressValidator.validatePostcode(Some("not a postcode"), blacklistedPostcodes) shouldBe Invalid(errorsForInvalidPostcode)
    }

    "return an error if format is invalid but contains a valid postcode" in {
      addressValidator.validatePostcode(Some(s"not a postcode $validPostcode not a postcode"), blacklistedPostcodes) shouldBe Invalid(errorsForInvalidPostcode)
    }

    "return an error if the postcode is empty" in {
      addressValidator.validatePostcode(Some(""), blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
    }

    "return an error if there is no postcode (None)" in {
      addressValidator.validatePostcode(None, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
    }

    "return an error if the postcode is blacklisted regardless of spacing" in {
      addressValidator.validatePostcode(Some("BB11BB"), blacklistedPostcodes) shouldBe Invalid(errorsForBlacklistedPostcode)
      addressValidator.validatePostcode(Some(blacklistedPostcode), blacklistedPostcodes) shouldBe Invalid(errorsForBlacklistedPostcode)
    }
  }

  "validateAddress" should {
    "populate all DesAddress fields when the input address is valid, even when all input lines are the maximum allowed length" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(validLine, validLine2, validLine3, validLine4),
        postcode = Some(validPostcode),
        country = testCountry(code = validCountryCode)
      )
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Valid(DesAddress(
        addressLine1 = validLine,
        addressLine2 = Some(validLine2),
        addressLine3 = Some(validLine3),
        addressLine4 = Some(validLine4),
        postcode = Some(validPostcode),
        countryCode = validCountryCode
      ))
    }

    "not throw an error when there is only one line" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine))
      val validatedAddress = addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe None
      desAddress.addressLine3 shouldBe None
      desAddress.addressLine4 shouldBe None
    }

    "pass when only address line 1 is provided and the rest are defined but empty" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, "", "", ""))
      val validatedAddress = addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe Some("")
      desAddress.addressLine3 shouldBe Some("")
      desAddress.addressLine4 shouldBe Some("")
    }

    "pass when there are 5 address line's but log a warning with the utr inside" in {
      when(slf4jLogger.isWarnEnabled).thenReturn(true)
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, validLine, validLine, validLine,validLine))
      val validatedAddress = addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe Some(validLine)
      desAddress.addressLine3 shouldBe Some(validLine)
      desAddress.addressLine4 shouldBe Some(validLine)
      verify(slf4jLogger).warn(s"UTR with more than 4 address lines: ${utrTest.value}")
    }

    "fail when address line 1 is present but invalid" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(tooLongAndNonMatchingLine))
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(errorsForTooLongAndNonMatchingLine)
    }

    "fail when no lines are provided" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq())
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(NonEmptyList.of(ValidationError("error.address.lines.empty")))
    }

    "pass when only a few address lines are provided" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, "", "", validLine2))
      val validatedAddress = addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes)
      val desAddress: DesAddress = validatedAddress.toOption.value
      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe Some("")
      desAddress.addressLine3 shouldBe Some("")
      desAddress.addressLine4 shouldBe Some(validLine2)
    }

    "validate that postcode is valid" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some("not a valid postcode"))
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(errorsForInvalidPostcode)
    }

    "pass on the postcode blacklist so that postcode blacklisting works" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some(blacklistedPostcode))
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(errorsForBlacklistedPostcode)
    }

    "accumulate errors for all fields" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(
          tooLongAndNonMatchingLine,
          nonMatchingLine,
          tooLongLine
        ),
        postcode = Some(blacklistedPostcode)
      )
      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Invalid(
        errorsForTooLongAndNonMatchingLine concat errorsForNonMatchingLine concat errorsForTooLongLine concat errorsForBlacklistedPostcode)
    }

    "be successful for even if 5th address line exists and 5th line is not valid (because 5th line is ignored)" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(validLine, validLine2, validLine3, validLine4, tooLongAndNonMatchingLine),
        postcode = Some(validPostcode),
        country = testCountry(code = validCountryCode))

      addressValidator.validateAddress(utrTest, addressLookupFrontendAddress, blacklistedPostcodes) shouldBe Valid(DesAddress(
        addressLine1 = validLine,
        addressLine2 = Some(validLine2),
        addressLine3 = Some(validLine3),
        addressLine4 = Some(validLine4),
        postcode = Some(validPostcode),
        countryCode = validCountryCode)
      )
    }
  }

  "optionInside" should {
    "convert a Validated inside an Option to an Option inside a Validated" in {
      val validValue = "I am valid"
      addressValidator.optionInside(Some(Valid(validValue))) shouldBe Valid(Some(validValue))

      addressValidator.optionInside(None) shouldBe Valid(None)

      val errors = NonEmptyList.of(ValidationError("error.test"))
      addressValidator.optionInside(Some(Invalid(errors))) shouldBe Invalid(errors)
    }
  }

  // remove implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
