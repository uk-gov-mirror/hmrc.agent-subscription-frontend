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

import play.api.data.Forms.{of, optional, text}
import play.api.data.{FormError, Mapping}
import play.api.data.format.Formatter
import play.api.data.validation._
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.domain.Nino

object CommonValidators {
  private val DesPostcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
  private val PostcodeSpecialCharsRegex = """^[A-Za-z0-9 ]*$"""
  private val EmailSpecialCharsRegex = """^[a-zA-Z 0-9\.\@\_\-]*$"""
  private val DesTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"

  private type UtrErrors = (String, String)
  private val DefaultUtrErrors = ("error.utr.blank", "error.utr.invalid")

  private val EmailMaxLength = 132
  private val PostcodeMaxLength = 8
  private val AddresslineMaxLength = 35
  private val BusinessNameMaxLength = 40
  private val UtrMaxLength = 10
  private val SaAgentCodeMaxLength = 6

  def saAgentCode = text verifying saAgentCodeConstraint

  def utr: Mapping[String] = text verifying utrConstraint()

  def clientDetailsUtr: Mapping[String] =
    text verifying utrConstraint(("error.client.sautr.blank", "error.client.sautr.invalid"))

  def businessUtr(businessType: String): Mapping[String] = {
    val utrErrors = businessType match {
      case "sole_trader" =>
        ("error.sautr.blank", "error.sautr.invalid")
      case "limited_company" =>
        ("error.companyutr.blank", "error.companyutr.invalid")
      case "partnership" | "llp" =>
        ("error.partnershiputr.blank", "error.partnershiputr.invalid")
      case _ =>
        DefaultUtrErrors
    }

    text verifying utrConstraint(utrErrors)
  }

  def clientDetailsNino: Mapping[String] = text verifying ninoConstraint

  def postcode: Mapping[String] =
    of[String](stringFormatWithMessage("error.postcode.empty")) verifying nonEmptyPostcode

  def postcodeWithBlacklist(blacklistedPostcodes: Set[String]): Mapping[String] =
    postcode
      .verifying("error.postcode.blacklisted", x => validateBlacklist(x, blacklistedPostcodes))

  def emailAddress: Mapping[String] =
    text
      .verifying(nonEmptyEmailAddress)

  def businessName: Mapping[String] =
    text
      .verifying(maxLength(BusinessNameMaxLength, "error.business-name.maxlength"))
      .verifying(
        checkOneAtATime(
          noAmpersand("error.business-name.invalid"),
          checkOneAtATime(
            noApostrophe("error.business-name.invalid"),
            desText(msgKeyRequired = "error.business-name.empty", msgKeyInvalid = "error.business-name.invalid"))
        ))

  def addressLine1: Mapping[String] =
    text
      .verifying(maxLength(AddresslineMaxLength, "error.addressline.1.maxlength"))
      .verifying(desText(msgKeyRequired = "error.addressline.1.empty", msgKeyInvalid = "error.addressline.1.invalid"))

  def addressLine234(lineNumber: Int): Mapping[Option[String]] =
    optional(
      text
        .verifying(maxLength(AddresslineMaxLength, s"error.addressline.$lineNumber.maxlength"))
        .verifying(
          desText(
            msgKeyRequired = s"error.addressline.$lineNumber.empty",
            msgKeyInvalid = s"error.addressline.$lineNumber.invalid")))

  def radioInputSelected[T](message: String = "error.no-radio-selected"): Constraint[Option[T]] =
    Constraint[Option[T]] { fieldValue: Option[T] =>
      if (fieldValue.isDefined)
        Valid
      else
        Invalid(ValidationError(message))
    }

  def nonEmptyTextWithMsg(errorMessageKey: String): Mapping[String] =
    text verifying nonEmptyWithMessage(errorMessageKey)

  // Same as play.api.data.validation.Constraints.nonEmpty but with a custom message instead of error.required
  private def nonEmptyWithMessage(messageKey: String): Constraint[String] = Constraint[String] { (o: String) =>
    if (o == null) Invalid(ValidationError(messageKey))
    else if (o.trim.isEmpty) Invalid(ValidationError(messageKey))
    else Valid
  }

  // Same as play.api.data.validation.Constraints.maxLength but with a chance to use a custom message instead of error.maxLength
  private def maxLength(length: Int, messageKey: String = "error.maxLength"): Constraint[String] =
    Constraint[String]("constraint.maxLength", length) { o =>
      require(length >= 0, "string maxLength must not be negative")
      if (o == null) Invalid(ValidationError(messageKey, length))
      else if (o.size <= length) Valid
      else Invalid(ValidationError(messageKey, length))
    }

  // Same as play.api.data.format.Formats.stringFormat but with a custom message instead of error.required
  private def stringFormatWithMessage(messageKey: String): Formatter[String] = new Formatter[String] {
    def bind(key: String, data: Map[String, String]) = data.get(key).toRight(Seq(FormError(key, messageKey, Nil)))

    def unbind(key: String, value: String) = Map(key -> value)
  }

  private def nonEmptyEmailAddress = Constraint { fieldValue: String =>
    nonEmptyWithMessage("error.business-email.empty")(fieldValue) match {
      case i: Invalid =>
        i
      case Valid =>
        fieldValue match {
          case value if value.size > EmailMaxLength =>
            Invalid(ValidationError("error.email.maxlength"))
          case value if !value.matches(EmailSpecialCharsRegex) =>
            Invalid(ValidationError("error.email.invalidchars"))
          case _ => Constraints.emailAddress(fieldValue)
        }
    }
  }

  private val nonEmptyPostcode: Constraint[String] = Constraint[String] { fieldValue: String =>
    nonEmptyWithMessage("error.postcode.empty")(fieldValue) match {
      case i: Invalid =>
        i
      case Valid =>
        fieldValue match {
          case value if value.size > PostcodeMaxLength => Invalid(ValidationError("error.postcode.maxlength"))
          case value if !value.matches(PostcodeSpecialCharsRegex) =>
            Invalid(ValidationError("error.postcode.invalidchars"))
          case value if !value.matches(DesPostcodeRegex) => Invalid(ValidationError("error.postcode.invalid"))
          case _                                         => Valid
        }
    }
  }

  private def noAmpersand(errorMsgKey: String) = Constraints.pattern("[^&]*".r, error = errorMsgKey)

  private def noApostrophe(errorMsgKey: String) = Constraints.pattern("[^']*".r, error = errorMsgKey)

  private[validators] def desText(msgKeyRequired: String, msgKeyInvalid: String): Constraint[String] =
    Constraint[String] { fieldValue: String =>
      nonEmptyWithMessage(msgKeyRequired)(fieldValue) match {
        case i: Invalid => i
        case Valid =>
          fieldValue match {
            case value if !value.matches(DesTextRegex) => Invalid(ValidationError(msgKeyInvalid))
            case _                                     => Valid
          }
      }
    }

  def validateBlacklist(postcode: String, blacklistedPostcodes: Set[String]): Boolean =
    !blacklistedPostcodes.contains(PostcodesLoader.formatPostcode(postcode))

  private val saAgentCodeConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
    val formattedCode = fieldValue.replace(" ", "")

    if (formattedCode.isEmpty)
      Invalid(ValidationError("error.saAgentCode.blank"))
    else if (!formattedCode.matches("""^[a-zA-Z0-9]*$"""))
      Invalid(ValidationError("error.saAgentCode.invalid"))
    else if (formattedCode.length != SaAgentCodeMaxLength)
      Invalid(ValidationError("error.saAgentCode.length"))
    else
      Valid
  }

  private def checkOneAtATime[T](firstConstraint: Constraint[T], secondConstraint: Constraint[T]) = Constraint[T] {
    fieldValue: T =>
      firstConstraint(fieldValue) match {
        case i @ Invalid(_) => i
        case Valid          => secondConstraint(fieldValue)
      }
  }

  private def utrConstraint(errorMessages: UtrErrors = DefaultUtrErrors): Constraint[String] = Constraint[String] {
    fieldValue: String =>
      val formattedField = fieldValue.replace(" ", "")
      val (blank, invalid) = errorMessages

      def isNumber(str: String): Boolean = str.map(_.isDigit).reduceOption(_ && _).getOrElse(false)

      Constraints.nonEmpty(formattedField) match {
        case _: Invalid => Invalid(ValidationError(blank))
        case _ if !isNumber(formattedField) || formattedField.size != UtrMaxLength =>
          Invalid(ValidationError(invalid))
        case _ => Valid
      }
  }

  private val ninoConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
    val formattedField = fieldValue.replaceAll("\\s", "")

    Constraints.nonEmpty(formattedField) match {
      case _: Invalid                         => Invalid(ValidationError("error.clientdetails.nino.empty"))
      case _ if !Nino.isValid(formattedField) => Invalid(ValidationError("error.clientdetails.nino.invalid"))
      case _                                  => Valid
    }
  }
}
