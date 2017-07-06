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
import play.api.libs.json.{OFormat, _}
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader

case class Address(addressLine1: String,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   postcode: Option[String],
                   countryCode: String)

object Address{
  type ValidatedType = Validated[Set[ValidationError], Unit]
  type ValidatedAddress = Validated[ValidationError, String]

  private val postCodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r
  private val desTextRegex = "^[A-Za-z0-9 \\-,.&'\\/]*$"
  private val maxLength = 35

  object ValidatedType {
    implicit val validationResultMonoid = new Monoid[ValidatedType] {
      def empty: ValidatedType = Valid(())

      def combine(x: ValidatedType, y: ValidatedType): ValidatedType = (x, y) match {
        case (Invalid(a), Invalid(b)) => Invalid(a ++ b)
        case (Valid(()), Valid(())) => Valid(())
        case (i@Invalid(_), _) => i
        case (_, i@Invalid(_)) => i
      }
    }
  }

  def validate(address: Address, blacklistedPostCodes: Set[String]): ValidatedType = {
    import ValidatedType._

    Monoid[ValidatedType].combineAll(List(validateLine(address.addressLine1), validateLine(address.addressLine2.getOrElse("")),
      validateLine(address.addressLine3.getOrElse("")), nonEmpty(address.postcode),
      validateRegex(address.postcode), validateBlacklist(address.postcode, blacklistedPostCodes)))
  }

  private def validateLine(line: String): ValidatedType = {
    validateLength(line, maxLength)
      .andThen(str =>
        validateDesRegex(str))
      .leftMap(error => Set(error))
      .andThen(str => Valid(()))
  }

  private def validateLength(line: String, maxLength: Int): ValidatedAddress = {
    if (line.length <= maxLength) Valid(line) else Invalid(ValidationError("error.address.maxLength", 35, line))
  }

  private def validateDesRegex(line: String): ValidatedAddress = {
    if (line.matches(desTextRegex)) Valid(line) else Invalid(ValidationError("error.des.text.invalid.withInput", line))
  }

  private def nonEmpty(postcode: Option[String]): ValidatedType = {
    postcode match {
      case Some("") => Invalid(Set(ValidationError("error.postcode.empty")))
      case Some(_) => Valid(())
      case None => Invalid(Set(ValidationError("error.postcode.empty")))
    }
  }

  private def validateRegex(postcode: Option[String]): ValidatedType = {
    postcode.map(str => postCodeRegex.unapplySeq(str.trim))
      .map(_ => Valid(()))
      .getOrElse(Invalid(Set(ValidationError("error.postcode.invalid"))))
  }

  def validateBlacklist(postcode: Option[String], blacklistedPostCodes: Set[String]): ValidatedType = {
    postcode.map(str =>
      blacklistedPostCodes.contains(PostcodesLoader.formatPostcode(str)) match {
        case true => Invalid(Set(ValidationError("error.postcode.blacklisted")))
        case false => Valid(())
      }).getOrElse(Invalid(Set(ValidationError("error.postcode.empty"))))
  }


  implicit val format: OFormat[Address] = {
    implicit val formatAddressValue = Json.format[Address]

    implicit val reads: Reads[Address] = Reads(json => {
      val addressLines = (json \ "address").as[JsObject]
      val addresses = (addressLines \ "lines").as[List[String]]
      val county = (addressLines \ "county").asOpt[String]
      val town = (addressLines \ "town").asOpt[String]
      val postcode = (addressLines \ "postcode").asOpt[String]
      val countryCode = (addressLines \ "country" \ "code").as[String]

      def merge(a: Option[String], b: Option[String]): Option[String] = (a, b) match {
        case (Some(s1), Some(s2)) => Some(s1 + " " + s2)
        case (None, s) => s
        case (s, None) => s
      }

      addresses.size match {
        case 4 => JsSuccess(
          Address(addresses.head, merge(merge(Some(addresses(1)), Some(addresses(2))),
            Some(addresses(3))), town, county, postcode, countryCode))

        case 3 => JsSuccess(Address(addresses.head, merge(Some(addresses(1)), Some(addresses(2))),
          town, county, postcode, countryCode))

        case 2 => JsSuccess(Address(addresses.head, Some(addresses(1)), town,
          county, postcode, countryCode))

        case 1 => JsSuccess(Address(addresses.head, town, county,
          None, postcode, countryCode))

        case _ => JsError(s"Address is empty from ADDRESS_LOOKUP service, $json")
      }

    })

    OFormat[Address](reads, formatAddressValue)
  }
}