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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.kernel.Monoid
import play.api.Play.current
import play.api.data.validation.ValidationError
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.AddressLookupFrontendAddress.ValidatedDesAddress._

case class Country(code: String,
                   name: Option[String])

case class AddressLookupFrontendAddress(
  lines: Seq[String],
  postcode: Option[String],
  country: Country
)

case class DesAddress(addressLine1: String,
                      addressLine2: Option[String] = None,
                      addressLine3: Option[String] = None,
                      addressLine4: Option[String],
                      postcode: Option[String],
                      countryCode: String)

object AddressLookupFrontendAddress {

  implicit val formatCountry = Json.format[Country]
  implicit val formatAddressLookupAddress = Json.format[AddressLookupFrontendAddress]

  type ValidatedDesAddress[T] = Validated[Set[ValidationError], T]

  private val postCodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r
  private val desTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"
  private val maxLength = 35

  object ValidatedDesAddress {
    implicit val validationResultMonoid = new Monoid[ValidatedDesAddress[DesAddress]] {
      def empty: ValidatedDesAddress[DesAddress] = Valid(DesAddress("",None,None,None,None,""))

      def combine(x: ValidatedDesAddress[DesAddress], y: ValidatedDesAddress[DesAddress]): ValidatedDesAddress[DesAddress] = (x, y) match {
        case (Invalid(a), Invalid(b)) => Invalid(a ++ b)
        case (Valid(a), Valid(b)) => Valid(b)
        case (i@Invalid(_), _) => i
        case (_, i@Invalid(_)) => i
      }
    }
  }

  def validate(address: AddressLookupFrontendAddress, blacklistedPostCodes: Set[String]): ValidatedDesAddress[DesAddress] = {
    Monoid[ValidatedDesAddress[DesAddress]].combineAll(List(validateLines(address.lines, address),
      nonEmpty(address.postcode, address),
      validateRegex(address.postcode, address), validateBlacklist(address.postcode, blacklistedPostCodes, address)))

  }

  private def toDesAddress(addressLookupAddress: AddressLookupFrontendAddress): DesAddress = addressLookupAddress.lines.size match {
    case 1 =>
      DesAddress(addressLookupAddress.lines(0), None, None, None, addressLookupAddress.postcode,
        addressLookupAddress.country.code)
    case 2 =>
      DesAddress(addressLookupAddress.lines(0), Some(addressLookupAddress.lines(1)),
        None, None, addressLookupAddress.postcode,
        addressLookupAddress.country.code)
    case 3 =>
     DesAddress(addressLookupAddress.lines(0), Some(addressLookupAddress.lines(1)),
       Some(addressLookupAddress.lines(2)), None, addressLookupAddress.postcode,
      addressLookupAddress.country.code)
    case _ =>
      DesAddress(addressLookupAddress.lines(0), Some(addressLookupAddress.lines(1)),
        Some(addressLookupAddress.lines(2)), Some(addressLookupAddress.lines(3)), addressLookupAddress.postcode,
        addressLookupAddress.country.code)

  }

  private def validateLine(line: String, addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    Monoid[ValidatedDesAddress[DesAddress]].combineAll(List(validateLength(line, addressLookupAddress),
      validateDesRegex(line, addressLookupAddress)))
  }

  private def validateLength(line: String, addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    if (line.length <= maxLength) Valid(toDesAddress(addressLookupAddress)) else Invalid(Set(ValidationError("error.address.maxLength", maxLength, line)))
  }

  private def validateDesRegex(line: String, addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    if (line.matches(desTextRegex)) Valid(toDesAddress(addressLookupAddress)) else Invalid(Set(ValidationError("error.des.text.invalid.withInput", line)))
  }

  private def nonEmpty(postcode: Option[String], addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    postcode match {
      case Some("") => Invalid(Set(ValidationError("error.postcode.empty")))
      case Some(_) => Valid(toDesAddress(addressLookupAddress))
      case None => Invalid(Set(ValidationError("error.postcode.empty")))
    }
  }

  private def validateRegex(postcode: Option[String], addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    postcode.map(str => postCodeRegex.unapplySeq(str.trim))
      .map(_ => Valid(toDesAddress(addressLookupAddress)))
      .getOrElse(Invalid(Set(ValidationError("error.postcode.invalid"))))
  }

  def validateBlacklist(postcode: Option[String], blacklistedPostCodes: Set[String],
                        addressLookupAddress: AddressLookupFrontendAddress): ValidatedDesAddress[DesAddress] = {
    postcode.map(str =>
      blacklistedPostCodes.contains(PostcodesLoader.formatPostcode(str)) match {
        case true => Invalid(Set(ValidationError("error.postcode.blacklisted")))
        case false => Valid(toDesAddress(addressLookupAddress))
      }).getOrElse(Invalid(Set(ValidationError("error.postcode.empty"))))
  }

  private def validateLines(lines: Seq[String], addressLookupAddress: AddressLookupFrontendAddress):
  ValidatedDesAddress[DesAddress] = {
      Monoid[ValidatedDesAddress[DesAddress]].combineAll(
        addressLookupAddress.lines.take(4).map(line => validateLine(line, addressLookupAddress))
      )
  }

  def renderErrors(errors: Set[ValidationError]): String = errors
    .map(valError => Messages(valError.message, valError.args: _*))
    .reduceOption(_ + ", " + _)
    .getOrElse("")
}