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

import org.scalatest.EitherValues
import play.api.data.validation.{Invalid, Valid, ValidationError}
import play.api.data.{FormError, Mapping}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.play.test.UnitSpec

class FieldMappingsSpec extends UnitSpec with EitherValues {

  "utr bind" should {
    val utrMapping = FieldMappings.utr.withPrefix("testKey")

    def bind(fieldValue: String) = utrMapping.bind(Map("testKey" -> fieldValue))

    "accept valid UTRs" in {
      bind("2000000000") shouldBe Right(Utr("2000000000"))
    }

    "give \"error.required\" error when it is not supplied" in {
      utrMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.utr.empty\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.utr.empty")
    }

    "give \"error.utr.empty\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.utr.empty")
    }

    "give \"error.utr.invalid\" error" when {
      "it has more than 10 digits" in {
        bind("20000000000") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "it has fewer than 10 digits" in {
        bind("200000") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "it has non-digit characters" in {
        bind("200000000B") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "it has non-alphanumeric characters" in {
        bind("200000000!") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }

      "checksum fails" in {
        bind("2000000001") should matchPattern { case Left(List(FormError("testKey", List("error.utr.invalid"), _))) => }
      }
    }
  }

  "postcode bind" should {
    behave like aPostcodeValidatingMapping(FieldMappings.postcode)
  }

  def aPostcodeValidatingMapping(unprefixedPostcodeMapping: Mapping[String]): Unit = {

    val postcodeMapping: Mapping[String] = unprefixedPostcodeMapping.withPrefix("testKey")

    def bind(fieldValue: String) = postcodeMapping.bind(Map("testKey" -> fieldValue))

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.postcode.invalid"), _))) => }
    }

    "accept valid postcodes" in {
      shouldAcceptFieldValue("AA1 1AA")
      shouldAcceptFieldValue("AA1M 1AA")
      shouldAcceptFieldValue("A11 1AA")
      shouldAcceptFieldValue("A1A 1AA")
    }

    "give \"error.postcode.empty\" error when it is set to null" in {
      bind(null).left.value should contain only FormError("testKey", List("error.postcode.empty"))
    }

    "give \"error.postcode.empty\" error when it is not supplied" in {
      postcodeMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.postcode.empty")
    }

    "give \"error.postcode.empty\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.postcode.empty")
    }

    "give \"error.postcode.empty\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.postcode.empty")
    }

    "reject postcodes containing invalid characters" in {
      shouldRejectFieldValueAsInvalid("_A1 1AA")
      shouldRejectFieldValueAsInvalid("A.1 1AA")
      shouldRejectFieldValueAsInvalid("AA/ 1AA")
      shouldRejectFieldValueAsInvalid("AA1#1AA")
      shouldRejectFieldValueAsInvalid("AA1 ~AA")
      shouldRejectFieldValueAsInvalid("AA1 1$A")
      shouldRejectFieldValueAsInvalid("AA1 1A%")
    }

    "accept postcodes with 2 characters in the outbound part" in {
      shouldAcceptFieldValue("A1 1AA")
    }

    "accept postcodes with 4 characters in the outbound part" in {
      shouldAcceptFieldValue("AA1A 1AA")
      shouldAcceptFieldValue("AA11 1AA")
    }

    "reject postcodes where the 1st character of the outbound part is a number" in {
      shouldRejectFieldValueAsInvalid("1A1 1AA")
    }

    "reject postcodes where the length of the inbound part is not 3" in {
      shouldRejectFieldValueAsInvalid("AA1 1A")
      shouldRejectFieldValueAsInvalid("AA1 1AAA")
    }

    "reject postcodes where the 1st character of the inbound part is a letter" in {
      shouldRejectFieldValueAsInvalid("AA1 AAA")
    }

    "reject valid start of postcode but invalid after" in {
      shouldRejectFieldValueAsInvalid("AA1 AAA PPRRD")
    }

    "accept postcodes without spaces" in {
      shouldAcceptFieldValue("AA11AA")
    }

    "reject postcodes with extra spaces" in {
      shouldRejectFieldValueAsInvalid(" A A 1 1 A A ")
    }
  }

  "postcodeWithBlacklist bind" should {
    val blacklistedPostcode = "BB1 1BB"
    val blacklistedPostcodes: Set[String] = Set(blacklistedPostcode, "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

    val unprefixedPostcodeMapping = FieldMappings.postcodeWithBlacklist(blacklistedPostcodes)
    val postcodeMapping = unprefixedPostcodeMapping.withPrefix("testKey")

    behave like aPostcodeValidatingMapping(unprefixedPostcodeMapping)

    def bind(fieldValue: String): Either[Seq[FormError], String] = postcodeMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueContainingMessage(fieldValue: String, messageKey: String) = {
      bind(fieldValue).left.get should contain(FormError("testKey", List(messageKey), Seq()))
    }

    "return an error if the postcode is blacklisted" in {
      shouldRejectFieldValueContainingMessage(blacklistedPostcode, "error.postcode.blacklisted")
    }

    "return an error if postcode without whitespace is blacklisted" in {
      shouldRejectFieldValueContainingMessage("BB11BB", "error.postcode.blacklisted")
    }

    "return an error if postcode with whitespace is blacklisted" in {
      shouldRejectFieldValueContainingMessage("BB1     1BB", "error.postcode.blacklisted")
    }

    "return an error if postcode with lowercase characters is blacklisted" in {
      shouldRejectFieldValueContainingMessage("bb1 1bB", "error.postcode.blacklisted")
    }
  }

  "emailAddress bind" should {
    val emailAddress = FieldMappings.emailAddress.withPrefix("testKey")

    def bind(fieldValue: String) = emailAddress.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.email"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject email address" when {
      "field is not present" in {
        emailAddress.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain only FormError("testKey", "error.email.empty")
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.email.empty")
      }

      "not a valid email" in {
        shouldRejectFieldValueAsInvalid("bademail")
      }
    }

    "accept a valid email address" in {
      shouldAcceptFieldValue("valid@test.com")
    }
  }

  "telephoneNumber bind" should {
    val telephoneMapping = FieldMappings.telephone.withPrefix("testKey")

    def bind(fieldValue: String) = telephoneMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.telephone.invalid"), _))) => }
    }

    def shouldRejectFieldValueAsTooLong(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.telephone.maxLength"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject telephone numbers" when {
      "field is not present" in {
        telephoneMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain only FormError("testKey", "error.telephone.empty")
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.telephone.empty")
      }

      "more than 24 characters" in {
        shouldRejectFieldValueAsTooLong("999999999999999999999999999999999")
      }

      "valid telephone number then invalid characters" in {
        shouldRejectFieldValueAsInvalid("0207 567 8554dbvv")
      }

      "there is text in the field" in {
        shouldRejectFieldValueAsInvalid("0123 456 7890 EXT 123")
      }
    }

    "accept telephone numbers" when {

      "there are 3 digits" in {
        shouldAcceptFieldValue("123")
      }

      "there are valid symbols in the input" in {
        shouldAcceptFieldValue("+441234567890")
        shouldAcceptFieldValue("#441234567890")
        shouldAcceptFieldValue("(44)1234567890")
        shouldAcceptFieldValue("++441234567890")
      }

      "there is whitespace in the field" in {
        shouldAcceptFieldValue("0123 456 7890")
      }
    }
  }

  "desTextConstraint" should {

    val desTextConstraint = FieldMappings.desText("error.des.text.empty", "error.des.text.invalid")

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      desTextConstraint(fieldValue) shouldBe Invalid(ValidationError("error.des.text.invalid"))
    }

    def shouldRejectFieldValidAsRequired(fieldValue: String): Unit = {
      desTextConstraint(fieldValue) shouldBe Invalid(ValidationError("error.des.text.empty"))
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      desTextConstraint(fieldValue) shouldBe Valid
    }

    "reject text" when {

      "input is empty" in {
        shouldRejectFieldValidAsRequired("")
      }

      "input is only whitespace" in {
        shouldRejectFieldValidAsRequired("     ")
      }

      "there is an invalid character" in {
        shouldRejectFieldValueAsInvalid("My Agency street; City~City")
      }
    }

    "accept text" when {
      "there is text and numbers" in {
        shouldAcceptFieldValue("99 My Agency address")
      }

      "there are valid symbols in the input" in {
        shouldAcceptFieldValue("My Agency address/Street ")
        shouldAcceptFieldValue("Tester's Agency address/Street")
      }

      "there is a valid address" in {
        shouldAcceptFieldValue("My Agency address")
      }

      "there are more than 35 characters" in {
        shouldAcceptFieldValue("1234567891123456789212345678931234567")
      }
    }
  }

  "addressLine1 bind" should {
    val unprefixedAddressLine1Mapping = FieldMappings.addressLine1

    behave like anAddressLineValidatingMapping(unprefixedAddressLine1Mapping)

    val addressLine1Mapping = unprefixedAddressLine1Mapping.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine1Mapping.bind(Map("testKey" -> fieldValue))

    "reject the line" when {
      "field is not present" in {
        addressLine1Mapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain(FormError("testKey", "error.address.lines.empty"))
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain(FormError("testKey", "error.address.lines.empty"))
      }
    }
  }

  "addressLine 2, 3 and 4 bind" should {
    val nonOptionalAddressLine234Mapping: Mapping[String] = FieldMappings.addressLine234.transform(_.get, Some.apply)

    behave like anAddressLineValidatingMapping(nonOptionalAddressLine234Mapping)

    val addressLine23Mapping = FieldMappings.addressLine234.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine23Mapping.bind(Map("testKey" -> fieldValue))

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      if (fieldValue.isEmpty) bind(fieldValue) shouldBe Right(None)
      else bind(fieldValue) shouldBe Right(Some(fieldValue))
    }

    "reject the line" when {
      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.address.lines.empty")
      }
    }

    "accept the line" when {
      "field is empty" in {
        shouldAcceptFieldValue("")
      }
    }
  }

  private def anAddressLineValidatingMapping(unprefixedAddressLineMapping: Mapping[String]): Unit = {

    val addressLine1Mapping = unprefixedAddressLineMapping.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine1Mapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.address.lines.invalid"), _))) => }
    }

    def shouldRejectFieldValueAsTooLong(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Left(List(FormError("testKey", List("error.address.lines.maxLength"), List(35))))
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      if (fieldValue.isEmpty) bind(fieldValue) shouldBe Right(None)
      else bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject the line" when {
      "there is an character that is not allowed by the DES regex" in {
        shouldRejectFieldValueAsInvalid("My Agency street<script> City~City")
      }

      "the line is too long for DES" in {
        shouldRejectFieldValueAsTooLong("123456789012345678901234567890123456")
      }
    }

    "accept the line" when {
      "there is text and numbers" in {
        shouldAcceptFieldValue("99 My Agency address")
      }

      "there are valid symbols in the input" in {
        shouldAcceptFieldValue("My Agency address/Street ")
        shouldAcceptFieldValue("Tester's Agency address/Street")
      }

      "there is a valid address" in {
        shouldAcceptFieldValue("My Agency address")
      }

      "it is the maximum allowable length" in {
        shouldAcceptFieldValue("12345678901234567890123456789012345")
      }
    }

    "accumulate errors if there are multiple validation problems" in {
      val tooLongAndNonMatchingLine = "123456789012345678901234567890123456<"
      bind(tooLongAndNonMatchingLine) shouldBe Left(List(
        FormError("testKey", "error.address.lines.maxLength", Seq(35)),
        FormError("testKey", "error.address.lines.invalid", Seq())
      ))
    }
  }

  "agencyName bind" should {

    val agencyNameMapping = FieldMappings.agencyName.withPrefix("testKey")

    def bind(fieldValue: String) = agencyNameMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.agency-name.invalid"), _))) => }
    }

    def shouldRejectFieldValueAsTooLong(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.maxLength"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject Agency name" when {

      "there is an ampersand character" in {
        shouldRejectFieldValueAsInvalid("My Agency & Co")
      }

      "there is an apostrophe character" in {
        shouldRejectFieldValueAsInvalid("My Agency's Co")
      }

      "there is an invalid character" in {
        shouldRejectFieldValueAsInvalid("My Agency; His Agency #1")
      }

      "there are more than 40 characters" in {
        shouldRejectFieldValueAsTooLong("12345678911234567892123456789312345678941234567")
      }

      "input is empty" in {
        bind("").left.value should contain(FormError("testKey", "error.agency-name.empty"))
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.agency-name.empty")
      }

      "field is not present" in {
        agencyNameMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }
    }

    "accept Agency name" when {
      "there are valid characters" in {
        shouldAcceptFieldValue("My Agency")
        shouldAcceptFieldValue("My/Agency")
        shouldAcceptFieldValue("My--Agency")
        shouldAcceptFieldValue("My,Agency")
      }

      "there are numbers and letters" in {
        shouldAcceptFieldValue("The 100 Agency")
      }
    }
  }

  "SA Agent Reference" should {
    "pass validation when the length of the sa agent reference is 6 characters and all A-Z0-9" in {
      FieldMappings.isValidSaAgentCode("123456") shouldBe true
      FieldMappings.isValidSaAgentCode("AA1234") shouldBe true
      FieldMappings.isValidSaAgentCode("123AA9") shouldBe true
      FieldMappings.isValidSaAgentCode("aabb12") shouldBe true
    }

    "fail validation when the length of the sa agent reference is less than 6 characters" in {
      FieldMappings.isValidSaAgentCode("12345") shouldBe false
      FieldMappings.isValidSaAgentCode("AA123") shouldBe false
      FieldMappings.isValidSaAgentCode("B123") shouldBe false
    }

    "fail validation when the length of the sa agent reference is more than 6 characters" in {
      FieldMappings.isValidSaAgentCode("1234567") shouldBe false
      FieldMappings.isValidSaAgentCode("AAA4567") shouldBe false
      FieldMappings.isValidSaAgentCode("123456789") shouldBe false
    }

    "fail validation when the sa agent reference contains non alphanumeric characters" in {
      FieldMappings.isValidSaAgentCode("!%1234") shouldBe false
      FieldMappings.isValidSaAgentCode("AA123.") shouldBe false
      FieldMappings.isValidSaAgentCode("abc12+") shouldBe false
    }
  }

}
