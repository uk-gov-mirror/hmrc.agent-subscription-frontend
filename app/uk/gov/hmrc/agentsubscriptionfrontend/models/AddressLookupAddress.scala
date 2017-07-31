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
import play.api.data.validation.ValidationError
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.{OFormat, _}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader
import uk.gov.hmrc.agentsubscriptionfrontend.models.AddressLookupAddress.ValidatedDesAddress
import cats.implicits._

case class AddressLookupAddress(addressLine1: String,
                                addressLine2: Option[String] = None,
                                addressLine3: Option[String] = None,
                                addressLine4: Option[String] = None,
                                postcode: Option[String],
                                countryCode: String)

case class DesAddress(addressLine1: String,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   town: Option[String],
                   postcode: Option[String],
                   countryCode: String)

object AddressLookupAddress {
  type ValidatedDesAddress[T] = Validated[Set[ValidationError], T]
  type ValidatedStringField = Validated[ValidationError, String]

  private val postCodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r
  private val desTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"
  private val maxLength = 35

  object ValidatedDesAddress {
    implicit val validationResultMonoid = new Monoid[ValidatedDesAddress[DesAddress]] {
      def empty: ValidatedDesAddress[DesAddress] = Valid(DesAddress("",None,None,None,None,""))

      def combine(x: ValidatedDesAddress[DesAddress], y: ValidatedDesAddress[DesAddress]): ValidatedDesAddress[DesAddress] = (x, y) match {
        case (Invalid(a), Invalid(b)) => Invalid(a ++ b)
        case (Valid(a), Valid(b)) => Valid(a)
        case (i@Invalid(_), _) => i
        case (_, i@Invalid(_)) => i
      }
    }
  }

  def validate(address: AddressLookupAddress, blacklistedPostCodes: Set[String]): ValidatedDesAddress[DesAddress] = {
    import ValidatedDesAddress._

    Monoid[ValidatedDesAddress[DesAddress]].combineAll(List(validateLine(address.addressLine1, address),
      validateLine(address.addressLine2.getOrElse(""), address), validateLine(address.addressLine3.getOrElse(""), address),
      validateLine(address.addressLine4.getOrElse(""), address), nonEmpty(address.postcode, address),
      validateRegex(address.postcode, address), validateBlacklist(address.postcode, blacklistedPostCodes, address)))

  }

  private def toDesAddress(addressLookupAddress: AddressLookupAddress): DesAddress = {
    DesAddress(addressLookupAddress.addressLine1,addressLookupAddress.addressLine2,
      addressLookupAddress.addressLine3, addressLookupAddress.addressLine4, addressLookupAddress.postcode,
      addressLookupAddress.countryCode)
  }

  private def validateLine(line: String, addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    import ValidatedDesAddress._

    Monoid[ValidatedDesAddress[DesAddress]].combineAll(List(validateLength(line, addressLookupAddress),
      validateDesRegex(line, addressLookupAddress)))

  }

  private def validateLength(line: String, addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    if (line.length <= maxLength) Valid(toDesAddress(addressLookupAddress)) else Invalid(Set(ValidationError("error.address.maxLength", maxLength, line)))
  }

  private def validateDesRegex(line: String, addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    if (line.matches(desTextRegex)) Valid(toDesAddress(addressLookupAddress)) else Invalid(Set(ValidationError("error.des.text.invalid.withInput", line)))
  }

  private def nonEmpty(postcode: Option[String], addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    postcode match {
      case Some("") => Invalid(Set(ValidationError("error.postcode.empty")))
      case Some(_) => Valid(toDesAddress(addressLookupAddress))
      case None => Invalid(Set(ValidationError("error.postcode.empty")))
    }
  }

  private def validateRegex(postcode: Option[String], addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    postcode.map(str => postCodeRegex.unapplySeq(str.trim))
      .map(_ => Valid(toDesAddress(addressLookupAddress)))
      .getOrElse(Invalid(Set(ValidationError("error.postcode.invalid"))))
  }

  def validateBlacklist(postcode: Option[String], blacklistedPostCodes: Set[String],
                        addressLookupAddress: AddressLookupAddress): ValidatedDesAddress[DesAddress] = {
    postcode.map(str =>
      blacklistedPostCodes.contains(PostcodesLoader.formatPostcode(str)) match {
        case true => Invalid(Set(ValidationError("error.postcode.blacklisted")))
        case false => Valid(toDesAddress(addressLookupAddress))
      }).getOrElse(Invalid(Set(ValidationError("error.postcode.empty"))))
  }


  implicit val format: OFormat[AddressLookupAddress] = {
    implicit val formatAddressValue = Json.format[AddressLookupAddress]

    implicit val reads: Reads[AddressLookupAddress] = Reads(json => {
      val address = (json \ "address").as[JsObject]
      val addressLines = (address \ "lines").as[List[String]]
      val county = (address \ "county").asOpt[String]
      val town = (address \ "town").asOpt[String]
      val postcode = (address \ "postcode").asOpt[String]
      val countryCode = (address \ "country" \ "code").as[String]

      def merge(a: Option[String], b: Option[String]): Option[String] = (a, b) match {
        case (Some(s1), Some(s2)) => Some(s1 + " " + s2)
        case (None, s) => s
        case (s, None) => s
      }

      addressLines.size match {
        case 4 => JsSuccess(
          AddressLookupAddress(addressLines.head, merge(merge(Some(addressLines(1)), Some(addressLines(2))),
            Some(addressLines(3))), town, county, postcode, countryCode))

        case 3 => JsSuccess(AddressLookupAddress(addressLines.head, merge(Some(addressLines(1)), Some(addressLines(2))),
          town, county, postcode, countryCode))

        case 2 => JsSuccess(AddressLookupAddress(addressLines.head, Some(addressLines(1)), town,
          county, postcode, countryCode))

        case 1 => JsSuccess(AddressLookupAddress(addressLines.head, town, county,
          None, postcode, countryCode))

        case _ => JsError(s"Address is empty from ADDRESS_LOOKUP service, $json")
      }

    })

    OFormat[AddressLookupAddress](reads, formatAddressValue)
  }

  def renderErrors(errors: Set[ValidationError]): String = errors
    .map(valError => Messages(valError.message, valError.args: _*))
    .reduceOption(_ + ", " + _)
    .getOrElse("")
}