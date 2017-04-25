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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.SimpleObjectReads

object Arn {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
}

object Address {
  implicit val format: Format[Address] = Json.format[Address]
}

object Agency {
  implicit val format: Format[Agency] = Json.format[Agency]
}

object KnownFacts {
  implicit val format: Format[KnownFacts] = Json.format[KnownFacts]
}

object SubscriptionRequest {
  implicit val format: Format[SubscriptionRequest] = Json.format[SubscriptionRequest]
}

case class Arn(arn: String)

case class Address(addressLine1: String,
                   addressLine2: Option[String],
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   postcode: String,
                   countryCode: String
                  )

case class Agency(name: String,
                  address: Address,
                  telephone: String,
                  email: String
                 )

case class KnownFacts(postcode: String)

case class SubscriptionRequest(utr: Utr,
                               knownFacts: KnownFacts,
                               agency: Agency
                              )
