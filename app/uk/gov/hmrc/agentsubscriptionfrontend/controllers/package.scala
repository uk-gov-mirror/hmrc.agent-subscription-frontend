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
import play.api.data.validation.{Constraint, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

package object controllers {

  object FieldMappings {
    private val telephoneNumberMaxLength = 24
    private val minimumTelephoneDigits = 10
    private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r
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
        case Valid => (fieldValue.length, fieldValue.replaceAll("[^0-9]", "").length) match {
          case (length, digitCount) if length > telephoneNumberMaxLength || digitCount < minimumTelephoneDigits =>
            Invalid(ValidationError("error.telephone.invalid"))
          case _ => Valid
        }
      }
    }

    private val noAmpersand = Constraints.pattern("[^&]*".r, error = "error.no.ampersand")

    def utr: Mapping[Utr] = nonEmptyText.transform[Utr](Utr.apply,_.value).verifying("error.utr.invalid", utr => Utr.isValid(utr.value))
    def postcode: Mapping[String] = text verifying nonEmptyPostcode
    def telephoneNumber: Mapping[String] = text verifying nonEmptyTelephoneNumber
    def agencyName: Mapping[String] = nonEmptyText(maxLength = 40) verifying noAmpersand
  }
}
