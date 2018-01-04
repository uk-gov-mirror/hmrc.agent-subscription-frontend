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

package uk.gov.hmrc.agentsubscriptionfrontend.form

import play.api.LoggerLike
import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AddressLookupFrontendAddress, DesAddress}

class DesAddressForm(logger: LoggerLike, blacklistedPostcodes: Set[String]) {
  val form = Form[DesAddress](
    mapping(
      "addressLine1" -> FieldMappings.addressLine1,
      "addressLine2" -> FieldMappings.addressLine234,
      "addressLine3" -> FieldMappings.addressLine234,
      "addressLine4" -> FieldMappings.addressLine234,
      "postcode" -> FieldMappings.postcodeWithBlacklist(blacklistedPostcodes),
      "countryCode" -> text
    )(DesAddress.apply)(DesAddress.unapply)
  )

  def bindAddressLookupFrontendAddress(utr: Utr, addressLookupFrontendAddress: AddressLookupFrontendAddress): Form[DesAddress] = {
    if (addressLookupFrontendAddress.lines.length > 4) {
      logger.warn(s"More than 4 address lines for UTR: ${utr.value}, discarding lines 5 and up")
    }
    form.bind(
      Map(
        "addressLine1" -> lineIfPresent(addressLookupFrontendAddress.lines, 0).getOrElse(""),
        "addressLine2" -> lineIfPresent(addressLookupFrontendAddress.lines, 1).getOrElse(""),
        "addressLine3" -> lineIfPresent(addressLookupFrontendAddress.lines, 2).getOrElse(""),
        "addressLine4" -> lineIfPresent(addressLookupFrontendAddress.lines, 3).getOrElse(""),
        "postcode" -> addressLookupFrontendAddress.postcode.getOrElse(""),
        "countryCode" -> addressLookupFrontendAddress.country.code
      )
    )
  }

  private def lineIfPresent(lines: Seq[String], index: Int): Option[String] =
    if (lines.length > index) Some(lines(index))
    else None
}
