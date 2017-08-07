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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.instances.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import play.api.data.validation.ValidationError
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

import scala.util.matching.Regex

object AddressValidator {

  type ValidationErrors = NonEmptyList[ValidationError]

  private val maxLength = 35
  private val desTextRegex: Regex = "^[A-Za-z0-9 \\-,.&'\\/]*$".r
  private val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r

  def validateAddress(addressLookupFrontendAddress: AddressLookupFrontendAddress, blacklistedPostcodes: Set[String]): Validated[ValidationErrors, DesAddress] =
    (validateLine1(addressLookupFrontendAddress.lines)
     |@| validateOptionLine(lineIfPresent(addressLookupFrontendAddress.lines, 1))
     |@| validateOptionLine(lineIfPresent(addressLookupFrontendAddress.lines, 2))
     |@| validateOptionLine(lineIfPresent(addressLookupFrontendAddress.lines, 3))
     |@| validatePostcode(addressLookupFrontendAddress.postcode, blacklistedPostcodes)
      ).map { (addressLine1, maybeAddressLine2, maybeAddressLine3, maybeAddressLine4, postcode) =>
        DesAddress(addressLine1, maybeAddressLine2, maybeAddressLine3, maybeAddressLine4, postcode, addressLookupFrontendAddress.country.code)
      }

  private def lineIfPresent(lines: Seq[String], index: Int): Option[String] =
    if (lines.length > index) Some(lines(index))
    else None

  private def validateOptionLine(maybeString: Option[String]): Validated[ValidationErrors, Option[String]] = {
    val maybeValidated: Option[Validated[ValidationErrors, String]] = maybeString.map(validateLine)
    optionInside(maybeValidated)
  }

  type V[A] = Validated[ValidationErrors, A]
  private def optionInside(maybeValidated: Option[Validated[ValidationErrors, String]]): Validated[ValidationErrors, Option[String]] =
    maybeValidated.traverse[V, String](identity)

  def validateLine(line: String): Validated[ValidationErrors, String] =
    (validateLength(line) |@| validateDesRegex(line))
      .map { case _ => line }

  def validateLine1(lines: Seq[String]): Validated[ValidationErrors, String] = {
    lines.headOption
      .map(validateLine)
      .getOrElse(Invalid(NonEmptyList.of(ValidationError("error.address.lines.empty"))))
  }

  private def validateLength(line: String): Validated[ValidationErrors, String] =
    Valid(line)
      .ensure(NonEmptyList.of(ValidationError("error.address.maxLength", maxLength, line)))(_.length <= maxLength)

  private def validateDesRegex(line: String): Validated[ValidationErrors, String] =
    line match {
      case desTextRegex(_*) => Valid(line)
      case _ => Invalid(NonEmptyList.of(ValidationError("error.des.text.invalid.withInput", line)))
    }

  def validatePostcode(maybePostcode: Option[String], blacklistedPostcodes: Set[String]): Validated[ValidationErrors, Option[String]] = {
    validateNotEmpty(maybePostcode) andThen (maybePostcode => optionInside(maybePostcode.map(pc => validateNonEmptyPostcode(pc, blacklistedPostcodes))))
  }

  private def validateNotEmpty(maybePostcode: Option[String]): Validated[ValidationErrors, Option[String]] = maybePostcode match {
    case Some("") | None => Invalid(NonEmptyList.of(ValidationError("error.postcode.empty")))
    case Some(_) => Valid(maybePostcode)
  }

  private def validateNonEmptyPostcode(postcode: String, blacklistedPostcodes: Set[String]): Validated[ValidationErrors, String] =
    (validatePostcodeRegex(postcode) |@| validateBlacklist(postcode, blacklistedPostcodes)).map { case _ => postcode }

  private def validatePostcodeRegex(postcode: String): Validated[ValidationErrors, String] =
    postcode match {
      case postcodeRegex(_*) => Valid(postcode)
      case _ => Invalid(NonEmptyList.of(ValidationError("error.postcode.invalid")))
    }

  private def validateBlacklist(postcode: String, blacklistedPostcodes: Set[String]): Validated[ValidationErrors, String] =
    if (blacklistedPostcodes.contains(PostcodesLoader.formatPostcode(postcode))) {
      Invalid(NonEmptyList.of(ValidationError("error.postcode.blacklisted")))
    } else {
      Valid(postcode)
    }

}
