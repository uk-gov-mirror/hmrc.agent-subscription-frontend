/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

case class SubscriptionDetails(
  utr: Utr,
  knownFactsPostcode: String,
  name: String,
  email: String,
  address: DesAddress,
  amlsDetails: AMLSDetails)

object SubscriptionDetails {
  implicit val formatDesAddress: Format[DesAddress] = Json.format[DesAddress]
  implicit val formatSubscriptionDetails: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  implicit def mapper(
    utr: Utr,
    postcode: Postcode,
    registration: Registration,
    amlsDetails: AMLSDetails): SubscriptionDetails = {
    val desAddress = DesAddress(
      registration.address.addressLine1,
      registration.address.addressLine2,
      registration.address.addressLine3,
      registration.address.addressLine4,
      registration.address.postalCode.getOrElse(throw new Exception("Postcode should not be empty")),
      registration.address.countryCode
    )

    SubscriptionDetails(
      utr,
      postcode.value,
      registration.taxpayerName.getOrElse(""),
      registration.emailAddress.getOrElse(throw new Exception("email should not be empty")),
      desAddress,
      amlsDetails
    )
  }
}
