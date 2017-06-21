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

import play.api.libs.json.{OFormat, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.SimpleObjectReads

case class Arn(arn: String)

object Arn {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
}

case class Address(addressLine1: String,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   postcode: String,
                   countryCode: String)

object Address {

  implicit val format: OFormat[Address] = {
    implicit val formatAddressValue = Json.format[Address]

    implicit val reads: Reads[Address] = Reads(json => {
      val addressLines = (json \ "address").as[JsObject]
      val lineMap = (addressLines \ "lines").as[List[String]]
      val postcode = (addressLines \ "postcode").as[String]
      val countryCode = (addressLines \ "country" \ "code").as[String]

      JsSuccess(Address(lineMap(0), Some(lineMap(1)), Some(lineMap(2)),
        Some(lineMap(3)), postcode, countryCode))
    }
    )

    OFormat[Address](reads, formatAddressValue)
  }

}

case class Agency(name: String,
                  address: Address,
                  telephone: String,
                  email: String)

object Agency {
  implicit val format: Format[Agency] = Json.format[Agency]
}

case class KnownFacts(postcode: String)

object KnownFacts {
  implicit val format: Format[KnownFacts] = Json.format[KnownFacts]
}

case class SubscriptionRequest(utr: Utr,
                               knownFacts: KnownFacts,
                               agency: Agency)

object SubscriptionRequest {
  implicit val format: Format[SubscriptionRequest] = Json.format[SubscriptionRequest]
}
