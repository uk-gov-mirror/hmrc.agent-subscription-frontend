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

package uk.gov.hmrc.agentsubscriptionfrontend.validators

import org.scalatest.EitherValues
import play.api.data.validation.{Invalid, Valid, ValidationError}
import play.api.data.{FormError, Mapping}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators._

import scala.util.Random

class CommonValidatorsSpec extends UnitSpec with EitherValues {

  "utr bind" should {
    val utrMapping = utr.withPrefix("testKey")

    def bind(fieldValue: String) = utrMapping.bind(Map("testKey" -> fieldValue))

    "accept valid UTRs" in {
      bind("20000  00000") shouldBe Right("20000  00000")
    }

    "give \"error.utr.blank\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.utr.blank")
    }

    "give \"error.utr.blank\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.utr.blank")
    }

    "give \"error.utr.invalid\" error" when {
      "it has more than 10 digits" in {
        bind("20000000000") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }
      }

      "it has fewer than 10 digits" in {
        bind("200000") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }

        bind("20000000 0") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }
      }

      "it has non-digit characters" in {
        bind("200000000B") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }
      }

      "it has non-alphanumeric characters" in {
        bind("200000000!") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }
      }

      "checksum fails" in {
        bind("2000000001") should matchPattern {
          case Left(List(FormError("testKey", List("error.utr.invalid"), _))) =>
        }
      }
    }
  }

  "businessUtr bind" should {
    behave like aUtrValidatingMapping("sole_trader")(fieldValue =>
      businessUtr("sole_trader").withPrefix("testKey").bind(Map("testKey" -> fieldValue)))
    behave like aUtrValidatingMapping("limited_company")(fieldValue =>
      businessUtr("limited_company").withPrefix("testKey").bind(Map("testKey" -> fieldValue)))
    behave like aUtrValidatingMapping("partnership")(fieldValue =>
      businessUtr("partnership").withPrefix("testKey").bind(Map("testKey" -> fieldValue)))
    behave like aUtrValidatingMapping("llp")(fieldValue =>
      businessUtr("llp").withPrefix("testKey").bind(Map("testKey" -> fieldValue)))
  }

  "clientDetailsUtr bind" should {
    behave like aUtrValidatingMapping("clientDetails")(fieldValue =>
      clientDetailsUtr.withPrefix("testKey").bind(Map("testKey" -> fieldValue)))
  }

  def aUtrValidatingMapping(errorMessageFor: String)(bind: String => Either[Seq[FormError], String]) = {
    val (blank, invalid) = errorMessageFor match {
      case "sole_trader" =>
        ("error.sautr.blank", "error.sautr.invalid")
      case "limited_company" =>
        ("error.companyutr.blank", "error.companyutr.invalid")
      case "partnership" | "llp" =>
        ("error.partnershiputr.blank", "error.partnershiputr.invalid")
      case "clientDetails" =>
        ("error.client.sautr.blank", "error.client.sautr.invalid")
    }

    s"accept valid UTRs for $errorMessageFor" in {
      bind("20000  00000") shouldBe Right("20000  00000")
    }

    s"give blank error for $errorMessageFor" when {
      "it is empty" in {
        bind("").left.value should contain only FormError("testKey", blank)
      }

      "it only contains a space" in {
        bind(" ").left.value should contain only FormError("testKey", blank)
      }
    }

    s"give invalid length error $errorMessageFor" when {
      "it has more than 10 digits" in {
        bind("20000000000") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }
      }

      "it has fewer than 10 digits" in {
        bind("200000") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }

        bind("20000000 0") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }
      }
    }
    s"give invalid format error $errorMessageFor" when {
      "it has non-digit characters" in {
        bind("200000000B") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }
      }

      "it has non-alphanumeric characters" in {
        bind("200000000!") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }
      }

      "checksum fails" in {
        bind("2000000001") should matchPattern {
          case Left(List(FormError("testKey", List(invalid), _))) =>
        }
      }
    }
  }

  "postcode bind" should {
    behave like aPostcodeValidatingMapping(postcode)
  }

  def aPostcodeValidatingMapping(unprefixedPostcodeMapping: Mapping[String]): Unit = {

    val postcodeMapping: Mapping[String] = unprefixedPostcodeMapping.withPrefix("testKey")

    def bind(fieldValue: String) = postcodeMapping.bind(Map("testKey" -> fieldValue))

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      bind(fieldValue) shouldBe Right(fieldValue)

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.postcode.invalid"), _))) =>
      }

    def shouldRejectFieldValueAsInvalidChars(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.postcode.invalidchars"), _))) =>
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

    "give \"error.postcode.maxlength\" error when length exeeds 8 chars" in {
      bind("AAA101AAA").left.value should contain only FormError("testKey", "error.postcode.maxlength")
    }

    "reject postcode if its invalid" in {
      shouldRejectFieldValueAsInvalid("_A1 1AA")
    }

    "reject postcodes containing invalid characters" in {
      shouldRejectFieldValueAsInvalidChars("A.1 1AA")
      shouldRejectFieldValueAsInvalidChars("AA/ 1AA")
      shouldRejectFieldValueAsInvalidChars("AA1#1AA")
      shouldRejectFieldValueAsInvalidChars("AA1 ~AA")
      shouldRejectFieldValueAsInvalidChars("AA1 1$A")
      shouldRejectFieldValueAsInvalidChars("AA1 1A%")
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

    "accept postcodes without spaces" in {
      shouldAcceptFieldValue("AA11AA")
    }

    "reject postcodes with extra spaces" in {
      shouldRejectFieldValueAsInvalid("A A 1 1A")
    }
  }

  "postcodeWithBlacklist bind" should {
    val blacklistedPostcode = "BB1 1BB"
    val blacklistedPostcodes: Set[String] =
      Set(blacklistedPostcode, "CC1 1CC", "DD1 1DD").map(PostcodesLoader.formatPostcode)

    val unprefixedPostcodeMapping = postcodeWithBlacklist(blacklistedPostcodes)
    val postcodeMapping = unprefixedPostcodeMapping.withPrefix("testKey")

    behave like aPostcodeValidatingMapping(unprefixedPostcodeMapping)

    def bind(fieldValue: String): Either[Seq[FormError], String] = postcodeMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueContainingMessage(fieldValue: String, messageKey: String) =
      bind(fieldValue).left.get should contain(FormError("testKey", List(messageKey), Seq()))

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
    val emailAddress = CommonValidators.emailAddress.withPrefix("testKey")

    def bind(fieldValue: String) = emailAddress.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.email"), _))) => }

    def shouldRejectFieldValueAsInvalidChars(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.email.invalidchars"), _))) =>
      }

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      bind(fieldValue) shouldBe Right(fieldValue)

    "reject email address" when {
      "field is not present" in {
        emailAddress.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain only FormError("testKey", "error.business-email.empty")
      }

      "input has length more than 132 characters" in {
        bind(s"${Random.alphanumeric.take(132).mkString}@example.com").left.value should contain only FormError(
          "testKey",
          "error.email.maxlength")
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.business-email.empty")
      }

      "not a valid email" in {
        shouldRejectFieldValueAsInvalid("bademail")
      }

      "it contains invalid chars" in {
        shouldRejectFieldValueAsInvalidChars("bad+email@example.com")
        shouldRejectFieldValueAsInvalidChars("bad*email@example.com")
        shouldRejectFieldValueAsInvalidChars("bad?email@example.com")
        shouldRejectFieldValueAsInvalidChars("bad&^%email@example.com")
      }
    }

    "accept a valid email address" in {
      shouldAcceptFieldValue("valid@test.com")
      shouldAcceptFieldValue("valid.email@test.com")
      shouldAcceptFieldValue("valid_email@test.com")
      shouldAcceptFieldValue("valid-email@test.com")
      shouldAcceptFieldValue("valid-email.address@test.com")
      shouldAcceptFieldValue("valid-email._address@test.com")
    }
  }

  "desTextConstraint" should {

    val desTextConstraint = desText("error.des.text.empty", "error.des.text.invalid")

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit =
      desTextConstraint(fieldValue) shouldBe Invalid(ValidationError("error.des.text.invalid"))

    def shouldRejectFieldValidAsRequired(fieldValue: String): Unit =
      desTextConstraint(fieldValue) shouldBe Invalid(ValidationError("error.des.text.empty"))

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      desTextConstraint(fieldValue) shouldBe Valid

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
    val unprefixedAddressLine1Mapping = addressLine1

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
    val nonOptionalAddressLine234Mapping: Mapping[String] = addressLine234.transform(_.get, Some.apply)

    behave like anAddressLineValidatingMapping(nonOptionalAddressLine234Mapping)

    val addressLine23Mapping = addressLine234.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine23Mapping.bind(Map("testKey" -> fieldValue))

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      if (fieldValue.isEmpty) bind(fieldValue) shouldBe Right(None)
      else bind(fieldValue) shouldBe Right(Some(fieldValue))

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

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.address.lines.invalid"), _))) =>
      }

    def shouldRejectFieldValueAsTooLong(fieldValue: String): Unit =
      bind(fieldValue) shouldBe Left(List(FormError("testKey", List("error.address.lines.maxLength"), List(35))))

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      if (fieldValue.isEmpty) bind(fieldValue) shouldBe Right(None)
      else bind(fieldValue) shouldBe Right(fieldValue)

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
      bind(tooLongAndNonMatchingLine) shouldBe Left(
        List(
          FormError("testKey", "error.address.lines.maxLength", Seq(35)),
          FormError("testKey", "error.address.lines.invalid", Seq())))
    }
  }

  "businessName bind" should {

    val businessNameMapping = businessName.withPrefix("testKey")

    def bind(fieldValue: String) = businessNameMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.business-name.invalid"), _))) =>
      }

    def shouldRejectFieldValueAsTooLong(fieldValue: String): Unit =
      bind(fieldValue) should matchPattern {
        case Left(List(FormError("testKey", List("error.business-name.maxlength"), _))) =>
      }

    def shouldAcceptFieldValue(fieldValue: String): Unit =
      bind(fieldValue) shouldBe Right(fieldValue)

    "reject business name" when {

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
        bind("").left.value should contain(FormError("testKey", "error.business-name.empty"))
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.business-name.empty")
      }

      "field is not present" in {
        businessNameMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }
    }

    "accept business name" when {
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
    val saAgentCodeMapping = saAgentCode.withPrefix("testKey")

    def bind(fieldValue: String) = saAgentCodeMapping.bind(Map("testKey" -> fieldValue))

    "accept valid SaAgentCode" in {
      bind("SA1234") shouldBe Right("SA1234")
    }

    "give \"error.saAgentCode.blank\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.saAgentCode.blank")
    }

    "give \"error.saAgentCode.blank\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.saAgentCode.blank")
    }

    "give \"error.saAgentCode.length\" error" when {
      "it has more than 6 characters" in {
        bind("SA20000000000") should matchPattern {
          case Left(List(FormError("testKey", List("error.saAgentCode.length"), _))) =>
        }
      }

      "it has fewer than 6 characters" in {
        bind("SA200") should matchPattern {
          case Left(List(FormError("testKey", List("error.saAgentCode.length"), _))) =>
        }
      }

      "it has no-alphanumeric characters" in {
        bind("SA*$") should matchPattern {
          case Left(List(FormError("testKey", List("error.saAgentCode.invalid"), _))) =>
        }
        bind("SA**12") should matchPattern {
          case Left(List(FormError("testKey", List("error.saAgentCode.invalid"), _))) =>
        }

        bind("SA**12222") should matchPattern {
          case Left(List(FormError("testKey", List("error.saAgentCode.invalid"), _))) =>
        }
      }
    }
  }

  "Nino validation" should {
    val ninoMapping = clientDetailsNino.withPrefix("testKey")

    def bind(fieldValue: String) = ninoMapping.bind(Map("testKey" -> fieldValue))

    "accept valid Nino" in {
      bind("AA980984B") shouldBe Right("AA980984B")
    }

    "accept valid Nino with random Spaces" in {
      bind("AA   9 8 0 98 4     B      ") shouldBe Right("AA   9 8 0 98 4     B      ")
    }

    "reject with error when invalid Nino" in {
      bind("AAAAAAAA0").left.value should contain only FormError("testKey", "error.clientdetails.nino.invalid")
    }

    "reject with error when nino field is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.clientdetails.nino.empty")
    }

    "reject with error when nino field contain spaces only" in {
      bind("    ").left.value should contain only FormError("testKey", "error.clientdetails.nino.empty")
    }
  }
}
