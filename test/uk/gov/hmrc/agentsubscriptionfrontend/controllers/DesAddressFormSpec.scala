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

import org.mockito.Mockito.{verify, when}
import org.scalatest.EitherValues
import org.slf4j.Logger
import play.api.LoggerLike
import play.api.data.FormError
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.DesAddress
import uk.gov.hmrc.agentsubscriptionfrontend.support.{ResettingMockitoSugar, testAddressLookupFrontendAddress, testCountry}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DesAddressFormSpec extends UnitSpec with ResettingMockitoSugar with EitherValues {

  // Each of these valid lines should be the maximum allowed length, 35
  // chars, to ensure we test the edge case of validation passing when all
  // lines are the maximum allowed length
  private val validLine = "12345678901234567890123456789012345"
  private val validLine2 = "22345678901234567890123456789012345"
  private val validLine3 = "32345678901234567890123456789012345"
  private val validLine4 = "42345678901234567890123456789012345"

  private val tooLongLine = "123456789012345678901234567890123456"
  private def errorsForTooLongLine(key: String) = Seq(
    FormError(key, "error.address.lines.maxLength", Seq(35))
  )

  private val nonMatchingLine = "<"
  private def errorsForNonMatchingLine(key: String) = Seq(
    FormError(key, "error.address.lines.invalid", Seq())
  )

  private val tooLongAndNonMatchingLine = "123456789012345678901234567890123456<"
  private def errorsForTooLongAndNonMatchingLine(key: String) = Seq(
    FormError(key, "error.address.lines.maxLength", Seq(35)),
    FormError(key, "error.address.lines.invalid", Seq())
  )

  private val validPostcode = "AA1 1AA"
  private val errorsForInvalidPostcode = Seq(
    FormError("postcode", "error.postcode.invalid", Seq())
  )

  private val blacklistedPostcode = "BB1 1BB"
  private val errorsForBlacklistedPostcode = Seq(
    FormError("postcode", "error.postcode.blacklisted", Seq())
  )
  private val blacklistedPostcodes: Set[String] = Set(blacklistedPostcode, "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

  private val validCountryCode = "GB"
  private val utr = Utr("1234567890")
  private val slf4jLogger = resettingMock[Logger]
  private val logger = new LoggerLike {
    override val logger: Logger = slf4jLogger
  }

  private val desAddressForm = new DesAddressForm(logger, blacklistedPostcodes)

  "form" should {
    "populate all DesAddress fields when the input address is valid, even when all input lines are the maximum allowed length" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(validLine, validLine2, validLine3, validLine4),
        postcode = Some(validPostcode),
        country = testCountry(code = validCountryCode)
      )

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe empty

      validatedForm.value shouldBe Some(
        DesAddress(
          addressLine1 = validLine,
          addressLine2 = Some(validLine2),
          addressLine3 = Some(validLine3),
          addressLine4 = Some(validLine4),
          postcode = validPostcode,
          countryCode = validCountryCode
        ))
    }

    "not throw an error when there is only one line" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)
      validatedForm.errors shouldBe empty
      val desAddress = validatedForm.value.value

      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe None
      desAddress.addressLine3 shouldBe None
      desAddress.addressLine4 shouldBe None
    }

    "pass when only address line 1 is provided and the rest are defined but empty" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, "", "", ""))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)
      validatedForm.errors shouldBe empty
      val desAddress = validatedForm.value.value

      desAddress.addressLine1 shouldBe validLine
      // Some("") -> None translation is probably a side effect of us using Forms.optional in FieldMappings.addressLine234
      desAddress.addressLine2 shouldBe None
      desAddress.addressLine3 shouldBe None
      desAddress.addressLine4 shouldBe None
    }

    "pass when there are 5 address line's but log a warning with the utr inside" in {
      when(slf4jLogger.isWarnEnabled).thenReturn(true)
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, validLine, validLine, validLine,validLine))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)
      validatedForm.errors shouldBe empty
      val desAddress = validatedForm.value.value

      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe Some(validLine)
      desAddress.addressLine3 shouldBe Some(validLine)
      desAddress.addressLine4 shouldBe Some(validLine)
      verify(slf4jLogger).warn(s"More than 4 address lines for UTR: ${utr.value}, discarding lines 5 and up")
    }

    "fail when address line 1 is present but invalid" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(tooLongAndNonMatchingLine))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe errorsForTooLongAndNonMatchingLine("addressLine1")
    }

    "fail when no lines are provided" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq())

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe Seq(
        FormError("addressLine1", "error.address.lines.empty", Seq())
      )
    }

    "pass when only a few address lines are provided" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(lines = Seq(validLine, "", "", validLine2))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)
      validatedForm.errors shouldBe empty
      val desAddress = validatedForm.value.value

      desAddress.addressLine1 shouldBe validLine
      desAddress.addressLine2 shouldBe None
      desAddress.addressLine3 shouldBe None
      desAddress.addressLine4 shouldBe Some(validLine2)
    }

    "validate that postcode is valid" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some("not a valid postcode"))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe errorsForInvalidPostcode
    }

    "validate that postcode is not empty" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some(" "))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe Seq(FormError("postcode", "error.postcode.empty", Seq()))
    }

    "pass on the postcode blacklist so that postcode blacklisting works" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(postcode = Some(blacklistedPostcode))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe errorsForBlacklistedPostcode
    }

    "validate all lines + postcode and accumulate errors" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(
          tooLongAndNonMatchingLine,
          nonMatchingLine,
          tooLongLine,
          nonMatchingLine
        ),
        postcode = Some(blacklistedPostcode)
      )

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)

      validatedForm.errors shouldBe (errorsForTooLongAndNonMatchingLine("addressLine1")
                                     ++ errorsForNonMatchingLine("addressLine2")
                                     ++ errorsForTooLongLine("addressLine3")
                                     ++ errorsForNonMatchingLine("addressLine4")
                                     ++ errorsForBlacklistedPostcode)
    }

    "be successful for even if 5th address line exists and 5th line is not valid (because 5th line is ignored)" in {
      val addressLookupFrontendAddress = testAddressLookupFrontendAddress(
        lines = Seq(validLine, validLine2, validLine3, validLine4, tooLongAndNonMatchingLine),
        postcode = Some(validPostcode),
        country = testCountry(code = validCountryCode))

      val validatedForm = desAddressForm.bindAddressLookupFrontendAddress(utr, addressLookupFrontendAddress)
      validatedForm.errors shouldBe empty
      val desAddress = validatedForm.value.value

      desAddress shouldBe DesAddress(
        addressLine1 = validLine,
        addressLine2 = Some(validLine2),
        addressLine3 = Some(validLine3),
        addressLine4 = Some(validLine4),
        postcode = validPostcode,
        countryCode = validCountryCode
      )
    }
  }

  // remove implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
