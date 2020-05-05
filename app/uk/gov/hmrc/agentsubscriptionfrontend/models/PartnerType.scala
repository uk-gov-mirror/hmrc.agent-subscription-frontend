/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsResult, JsString, JsSuccess, JsValue, Json}

sealed trait PartnerType {
  val key: String
}

object PartnerType {

  case object IndividualPartner extends PartnerType {
    override val key = "individual_partner"
  }

  case object CorporatePartner extends PartnerType {
    override val key = "corporate_partner"
  }

  def apply(key: String): PartnerType = key match {
    case "individual_partner" => IndividualPartner
    case "corporate_partner"  => CorporatePartner
  }

  implicit val format: Format[PartnerType] = new Format[PartnerType] {

    override def reads(json: JsValue): JsResult[PartnerType] =
      json.as[String] match {
        case "individual_partner" => JsSuccess(IndividualPartner)
        case "corporate_partner"  => JsSuccess(CorporatePartner)
      }

    override def writes(o: PartnerType): JsValue = o match {
      case IndividualPartner => JsString("individual_partner")
      case CorporatePartner  => JsString("corporate_partner")
    }
  }
}
