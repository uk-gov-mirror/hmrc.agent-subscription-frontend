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
import play.api.libs.json._

sealed trait BusinessType {
  val key: String
}

object BusinessType {

  case object SoleTrader extends BusinessType {
    override val key: String = "sole_trader"
  }
  case object LimitedCompany extends BusinessType {
    override val key: String = "limited_company"
  }

  case object Partnership extends BusinessType {
    override val key: String = "partnership"
  }

  case object Llp extends BusinessType {
    override val key: String = "llp"
  }

  case object Invalid extends BusinessType {
    override val key: String = "inValid"
  }

  def apply(convertToType: String): BusinessType = convertToType match {
    case "sole_trader"     => SoleTrader
    case "limited_company" => LimitedCompany
    case "partnership"     => Partnership
    case "llp"             => Llp
    case _                 => Invalid
  }

  implicit val format: Format[BusinessType] = new Format[BusinessType] {

    override def reads(json: JsValue): JsResult[BusinessType] = {
      json.as[String] match {
        case "sole_trader"     => JsSuccess(SoleTrader)
        case "limited_company" => JsSuccess(LimitedCompany)
        case "partnership"     => JsSuccess(Partnership)
        case "llp"             => JsSuccess(Llp)
        case e                 => JsError(s"invalid value for BusinessType: $e")
      }
      JsSuccess(BusinessType.apply(json.as[String]))
    }

    override def writes(o: BusinessType): JsValue =
      o match {
        case SoleTrader     => JsString("sole_trader")
        case LimitedCompany => JsString("limited_company")
        case Partnership    => JsString("partnership")
        case Llp            => JsString("llp")
        case e              => throw new RuntimeException(s"invalid BusinessType: $e")
      }
  }
}
