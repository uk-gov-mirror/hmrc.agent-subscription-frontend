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

package uk.gov.hmrc.agentsubscriptionfrontend

import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.{Constraint, Constraints, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

package object controllers {

  object FieldMappings {
    private val telephoneNumberMaxLength = 24
    private val addressMaxLength = 35
    private val postcodeWithoutSpacesRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r
    private val addressRegex = "^[A-Za-z0-9 \\-,.&'\\/]{1,35}$"
    private val nonEmptyPostcode: Constraint[String] = Constraint[String] { fieldValue: String =>
      Constraints.nonEmpty(fieldValue) match {
        case i: Invalid =>
          i
        case Valid =>
          val error = "error.postcode.invalid"
          val fieldValueWithoutSpaces = fieldValue.replace(" ", "")
          postcodeWithoutSpacesRegex.unapplySeq(fieldValueWithoutSpaces)
            .map(_ => Valid)
            .getOrElse(Invalid(ValidationError(error)))
      }
    }

    private val nonEmptyTelephoneNumber: Constraint[String] = Constraint[String] { fieldValue: String =>
      Constraints.nonEmpty(fieldValue) match {
        case i: Invalid => i
        case Valid => (fieldValue.length, fieldValue.replaceAll("^[^0-9- +()#x ]{1,24}$", "")) match {
          case (length, digitCount) if length > telephoneNumberMaxLength =>
            Invalid(ValidationError("error.telephone.invalid"))
          case _ => Valid
        }
      }
    }

    private val noAmpersand = Constraints.pattern("[^&]*".r, error = "error.no.ampersand")

    private val addressConstraint: Constraint[String] = Constraint[String] { fieldValue: String =>
      Constraints.nonEmpty(fieldValue) match {
        case i: Invalid => i
        case Valid => fieldValue match{
          case value if value.length > addressMaxLength => Invalid(ValidationError("error.addressLine1.invalid"))
          case value if !value.matches(addressRegex) => Invalid(ValidationError("error.addressLine1.invalid"))
          case _ => Valid
        }
      }
    }

    private val address23Constraint: Constraint[String] = Constraint[String] { fieldValue: String =>
      Constraints.nonEmpty(fieldValue) match {
        case i: Invalid => i
        case Valid => fieldValue match{
          case value if value.length > addressMaxLength => Invalid(ValidationError("error.addressLine.invalid"))
          case value if !value.matches(addressRegex) => Invalid(ValidationError("error.addressLine.invalid"))
          case _ => Valid
        }
      }
    }

    def utr: Mapping[Utr] = nonEmptyText.transform[Utr](Utr.apply,_.value).verifying("error.utr.invalid", utr => Utr.isValid(utr.value))
    def postcode: Mapping[String] = text verifying nonEmptyPostcode
    def telephoneNumber: Mapping[String] = text verifying nonEmptyTelephoneNumber
    def agencyName: Mapping[String] = nonEmptyText(maxLength = 40) verifying noAmpersand
    def addressLine1: Mapping[String] = text verifying addressConstraint
    def addressLine23: Mapping[Option[String]] = optional(text verifying address23Constraint)
  }
}
