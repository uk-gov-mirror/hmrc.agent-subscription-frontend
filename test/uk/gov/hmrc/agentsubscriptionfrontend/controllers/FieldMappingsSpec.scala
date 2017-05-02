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
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
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

    "give \"error.required\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.required\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.required")
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
    val postcodeMapping = FieldMappings.postcode.withPrefix("testKey")

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

    "give \"error.required\" error when it is not supplied" in {
      postcodeMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.required\" error when it is empty" in {
      bind("").left.value should contain only FormError("testKey", "error.required")
    }

    "give \"error.required\" error when it only contains a space" in {
      bind(" ").left.value should contain only FormError("testKey", "error.required")
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

    "accept postcodes without spaces" in {
      shouldAcceptFieldValue("AA11AA")
    }

    "accept postcodes with extra spaces" in {
      shouldAcceptFieldValue(" A A 1 1 A A ")
    }
  }

  "telephone bind" should {
    val telephoneMapping = FieldMappings.telephoneNumber.withPrefix("testKey")

    def bind(fieldValue: String) = telephoneMapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.telephone.invalid"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject telephone numbers" when {
      "field is not present" in {
        telephoneMapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain only FormError("testKey", "error.required")
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.required")
      }

      "more than 24 characters" in {
        shouldRejectFieldValueAsInvalid("999999999999999999999999999999999")
      }

    }

    "accept telephone numbers" when {
      "there are 10 digits" in {
        shouldAcceptFieldValue("1234567 ext 123")
      }

      "there are 3 digits" in {
        shouldAcceptFieldValue("123")
      }

      "there are valid symbols in the input" in {
        shouldAcceptFieldValue("+441234567890")
        shouldAcceptFieldValue("#441234567890")
        shouldAcceptFieldValue("(44)1234567890")
        shouldAcceptFieldValue("/-*441234567890")
      }

      "there is text in the field" in {
        shouldAcceptFieldValue("0123 456 7890 EXT 123")
      }

      "there is whitespace in the field" in {
        shouldAcceptFieldValue("0123 456 7890")
      }
    }
  }

  "addressLine1 bind" should {
    val addressLine1Mapping = FieldMappings.addressLine1.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine1Mapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.addressLine1.invalid"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      bind(fieldValue) shouldBe Right(fieldValue)
    }

    "reject address Line 1" when {
      "field is not present" in {
        addressLine1Mapping.bind(Map.empty).left.value should contain only FormError("testKey", "error.required")
      }

      "input is empty" in {
        bind("").left.value should contain(FormError("testKey", "error.required"))
      }

      "input is only whitespace" in {
        bind("    ").left.value should contain(FormError("testKey", "error.required"))
      }

      "more than 35 characters" in {
        shouldRejectFieldValueAsInvalid("1234567891123456789212345678931234567")
      }

      "there is an invalid character" in {
        shouldRejectFieldValueAsInvalid("My Agency street; City~City")
      }
    }

    "accept address Line 1" when {
      "there is text and numbers" in {
        shouldAcceptFieldValue("99 My Agency address")
      }

      "there are valid symbols in the input" in {
        shouldAcceptFieldValue("My Agency address/Street ")
        shouldAcceptFieldValue("Tester's Agency address/Street")
      }

      "there is a valid address" in {
        shouldAcceptFieldValue( "My Agency address")
      }
    }
  }

  "address Line 2 and 3 bind" should {
    val addressLine23Mapping = FieldMappings.addressLine23.withPrefix("testKey")

    def bind(fieldValue: String) = addressLine23Mapping.bind(Map("testKey" -> fieldValue))

    def shouldRejectFieldValueAsInvalid(fieldValue: String): Unit = {
      bind(fieldValue) should matchPattern { case Left(List(FormError("testKey", List("error.addressLine.invalid"), _))) => }
    }

    def shouldAcceptFieldValue(fieldValue: String): Unit = {
      if (fieldValue.isEmpty) bind(fieldValue) shouldBe Right(None)
      else bind(fieldValue) shouldBe Right(Some(fieldValue))
    }

    "reject addressLine 2 and 3" when {

      "input is only whitespace" in {
        bind("    ").left.value should contain only FormError("testKey", "error.required")
      }

      "more than 35 characters" in {
        shouldRejectFieldValueAsInvalid("1234567891123456789212345678931234567")
      }

      "there is an invalid character" in {
        shouldRejectFieldValueAsInvalid("My Agency street; City~City")
      }
    }

    "accept address Line 2 and 3" when {
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

      "field is empty" in {
        shouldAcceptFieldValue("")
      }
    }
  }


}
