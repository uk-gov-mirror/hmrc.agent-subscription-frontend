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

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, InitialDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.validators.ValidationResult.FailureReason._
import uk.gov.hmrc.agentsubscriptionfrontend.validators.ValidationResult._
import uk.gov.hmrc.play.test.UnitSpec

class InitialDetailsValidatorSpec extends UnitSpec {

  private val utr = Utr("2000000000")
  private val knownFactsPostcode = "AA1 2AA"

  private val validBusinessAddress = BusinessAddress(
    "AddressLine1 A",
    Some("AddressLine2 A"),
    Some("AddressLine3 A"),
    Some("AddressLine4 A"),
    Some("AA11AA"),
    "GB")
  private val validInitialDetails =
    InitialDetails(
      utr,
      knownFactsPostcode,
      "My Agency",
      Some("agency@example.com"),
      validBusinessAddress
    )

  private val validator = new InitialDetailsValidator()

  "InitialDetailsValidator" should {
    "validate" should {
      "validate email address" when {
        "email is empty" in {
          validator.validate(validInitialDetails.copy(email = None)) shouldBe Failure(InvalidEmail)
        }
        "email is empty string" in {
          validator.validate(validInitialDetails.copy(email = Some(""))) shouldBe Failure(InvalidEmail)
        }
        "email contains white spaces only" in {
          validator.validate(validInitialDetails.copy(email = Some("  "))) shouldBe Failure(InvalidEmail)
        }
        "email contains invalid characters" in {
          validator.validate(validInitialDetails.copy(email = Some("someemail@examp!#$%&'*+/=?e.com"))) shouldBe Failure(
            InvalidEmail)
        }
        "email longer than 132 characters" in {
          validator.validate(validInitialDetails
            .copy(email = Some(s"""${"this-email-is-133-characters-long@".padTo(129, "a").mkString}.com"""))) shouldBe Failure(
            InvalidEmail)
        }
        "email has no localpart" in {
          validator.validate(validInitialDetails.copy(email = Some("@example.com"))) shouldBe Failure(InvalidEmail)
        }
        "email has no @ symbol" in {
          validator.validate(validInitialDetails.copy(email = Some("someemail.com"))) shouldBe Failure(InvalidEmail)

        }
        "email has no domain" in {
          validator.validate(validInitialDetails.copy(email = Some("someemail@"))) shouldBe Failure(InvalidEmail)
        }

        "return Pass if email is valid" in {
          validator.validate(validInitialDetails.copy(email = Some("someemail@example.com"))) shouldBe Pass
        }

      }

      "validate business name" when {
        "it's missing" in {
          validator.validate(validInitialDetails.copy(name = "")) shouldBe Failure(InvalidBusinessName)
        }

        "it's just whitespace" in {
          validator.validate(validInitialDetails.copy(name = "   ")) shouldBe Failure(InvalidBusinessName)
        }

        "it contains an ampersand" in {
          validator.validate(validInitialDetails.copy(name = "Some&name")) shouldBe Failure(InvalidBusinessName)
        }

        "it contains a single quote" in {
          validator.validate(validInitialDetails.copy(name = "Some'name")) shouldBe Failure(InvalidBusinessName)
        }

        "it contains backward slashes" in {
          validator.validate(validInitialDetails.copy(name = """Some valid name\""")) shouldBe Failure(
            InvalidBusinessName)
        }

        "it's longer than 40 characters (number of characters DES and ES8 will accept)" in {
          validator.validate(validInitialDetails.copy(name = "12345678911234567892123456789312345678941234567")) shouldBe Failure(
            InvalidBusinessName)
        }

        "it contains invalid characters" in {
          validator.validate(validInitialDetails.copy(name = "Some @#?;<> name")) shouldBe Failure(InvalidBusinessName)
        }

        "return Pass if the name is valid" in {
          validator.validate(validInitialDetails.copy(name = "Some valid name")) shouldBe Pass
        }

        "return Pass if the name contains forward slashes" in {
          validator.validate(validInitialDetails.copy(name = """Some valid name/""")) shouldBe Pass
        }
      }
    }
  }

}
