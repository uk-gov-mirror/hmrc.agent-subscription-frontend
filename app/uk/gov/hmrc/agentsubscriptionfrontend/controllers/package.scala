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

package uk.gov.hmrc.agentsubscriptionfrontend

import play.api.data.Forms._
import play.api.data.{Form, FormError, Mapping}
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Constraints, _}
import play.api.i18n.Messages
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

package object controllers {
  object FieldMappings {
    private val desPostcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r
    private val telephoneNumberRegex = "^[0-9- +()#x ]*$"
    private val desTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"

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
      nonEmptyWithMessage("error.email.empty")(fieldValue) match {
        case i: Invalid =>
          i
        case Valid =>
          Constraints.emailAddress(fieldValue)
      }
    }

    private val nonEmptyPostcode: Constraint[String] = Constraint[String] { fieldValue: String =>
      nonEmptyWithMessage("error.postcode.empty")(fieldValue) match {
        case i: Invalid =>
          i
        case Valid =>
          val error = "error.postcode.invalid"
          desPostcodeRegex
            .unapplySeq(fieldValue)
            .map(_ => Valid)
            .getOrElse(Invalid(ValidationError(error)))
      }
    }

    private val telephoneNumber: Constraint[String] = Constraint[String] { fieldValue: String =>
      nonEmptyWithMessage("error.telephone.empty")(fieldValue) match {
        case i: Invalid => i
        case Valid =>
          fieldValue match {
            case value if !value.matches(telephoneNumberRegex) =>
              Invalid(ValidationError("error.telephone.invalid"))
            case _ => Valid
          }
      }
    }

    private def noAmpersand(errorMsgKey: String) = Constraints.pattern("[^&]*".r, error = errorMsgKey)

    private def noApostrophe(errorMsgKey: String) = Constraints.pattern("[^']*".r, error = errorMsgKey)

    private[controllers] def desText(msgKeyRequired: String, msgKeyInvalid: String): Constraint[String] =
      Constraint[String] { fieldValue: String =>
        nonEmptyWithMessage(msgKeyRequired)(fieldValue) match {
          case i: Invalid => i
          case Valid =>
            fieldValue match {
              case value if !value.matches(desTextRegex) => Invalid(ValidationError(msgKeyInvalid))
              case _                                     => Valid
            }
        }
      }

    private def validateBlacklist(postcode: String, blacklistedPostcodes: Set[String]): Boolean =
      !blacklistedPostcodes.contains(PostcodesLoader.formatPostcode(postcode))

    private val saAgentCodeConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
      val formattedCode = fieldValue.replace(" ", "")

      if (formattedCode.isEmpty)
        Invalid(ValidationError("error.saAgentCode.blank"))
      else if (!formattedCode.matches("""^[a-zA-Z0-9]*$"""))
        Invalid(ValidationError("error.saAgentCode.invalid"))
      else if (formattedCode.length != 6)
        Invalid(ValidationError("error.saAgentCode.length"))
      else
        Valid
    }

    def saAgentCode = text verifying saAgentCodeConstraint

    private def checkOneAtATime[T](firstConstraint: Constraint[T], secondConstraint: Constraint[T]) = Constraint[T] {
      fieldValue: T =>
        firstConstraint(fieldValue) match {
          case i @ Invalid(_) => i
          case Valid          => secondConstraint(fieldValue)
        }
    }

    def normalizeUtr(utrStr: String): Option[Utr] = {
      val formattedUtr = utrStr.replace(" ", "")
      if (Utr.isValid(formattedUtr)) Some(Utr(formattedUtr)) else None
    }

    private def isUtrValid(utrStr: String): Boolean = normalizeUtr(utrStr).nonEmpty

    def prettify(arn: Arn): String = {
      val unapplyPattern = """([A-Z]ARN)(\d{3})(\d{4})""".r

      unapplyPattern
        .unapplySeq(arn.value)
        .map(_.mkString("-"))
        .getOrElse(throw new Exception(s"The arn contains an invalid value ${arn.value}"))
    }

    def prettify(utr: Utr): String =
      if (utr.value.trim.length == 10) {
        val (first, last) = utr.value.trim.splitAt(5)
        s"$first $last"
      } else {
        throw new Exception(s"The utr contains an invalid value ${utr.value}")
      }

    private val utrConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
      val formattedField = fieldValue.replace(" ", "")

      Constraints.nonEmpty(formattedField) match {
        case _: Invalid => Invalid(ValidationError("error.utr.blank"))
        case _ if formattedField.map(_.isDigit).reduce(_ && _) && formattedField.size != 10 =>
          Invalid(ValidationError("error.utr.invalid.length"))
        case _ if !isUtrValid(fieldValue) => Invalid(ValidationError("error.utr.invalid.format"))
        case _                            => Valid
      }
    }

    def utr: Mapping[String] = text verifying utrConstraint

    def postcode: Mapping[String] =
      of[String](stringFormatWithMessage("error.postcode.empty")) verifying nonEmptyPostcode

    def postcodeWithBlacklist(blacklistedPostcodes: Set[String]): Mapping[String] =
      postcode
        .verifying("error.postcode.blacklisted", x => validateBlacklist(x, blacklistedPostcodes))

    def telephone: Mapping[String] =
      text
        .verifying(maxLength(24, "error.telephone.maxLength"))
        .verifying(telephoneNumber)

    def emailAddress: Mapping[String] =
      text
        .verifying(nonEmptyEmailAddress)

    def agencyName: Mapping[String] =
      text(maxLength = 40)
        .verifying(
          checkOneAtATime(
            noAmpersand("error.agency-name.invalid"),
            checkOneAtATime(
              noApostrophe("error.agency-name.invalid"),
              desText(msgKeyRequired = "error.agency-name.empty", msgKeyInvalid = "error.agency-name.invalid"))
          ))

    def addressLine1: Mapping[String] =
      text
        .verifying(maxLength(35, "error.address.lines.maxLength"))
        .verifying(desText(msgKeyRequired = "error.address.lines.empty", msgKeyInvalid = "error.address.lines.invalid"))

    def addressLine234: Mapping[Option[String]] =
      optional(
        text
          .verifying(maxLength(35, "error.address.lines.maxLength"))
          .verifying(
            desText(msgKeyRequired = "error.address.lines.empty", msgKeyInvalid = "error.address.lines.invalid")))

    def radioInputSelected[T]: Constraint[Option[T]] = Constraint[Option[T]] { fieldValue: Option[T] =>
      if (fieldValue.isDefined)
        Valid
      else
        Invalid(ValidationError("error.no-radio-selected"))
    }

    def nonEmptyTextWithMsg(errorMessageKey: String): Mapping[String] =
      text verifying nonEmptyWithMessage(errorMessageKey)
  }

}
