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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.FieldMappings.{nino, radioInputSelected, saAgentCode, utr}
import uk.gov.voa.play.form.ConditionalMappings.{mandatoryIfEqual, mandatoryIfTrue}

case class RadioInvasiveStartSaAgentCode(hasSaAgentCode: Option[Boolean], saAgentCode: Option[String])
case class RadioInvasiveTaxPayerOption(variant: Option[String], utr: Option[String], nino: Option[String])

object ValidVariantsTaxPayerOptionForm extends Enumeration {
  type ValidVariantsTaxPayerOptionForm = String
  val UtrV = Value("utr")
  val NinoV = Value("nino")
  val CannotProvideV = Value("cannotProvide")
}

object RadioWithInput {
  //uses variant "cannotProvide" to determine action if user cannot provide allowed options: utr or nino
  val invasiveCheckTaxPayerOption: Form[RadioInvasiveTaxPayerOption] = Form[RadioInvasiveTaxPayerOption](
    mapping(
      "variant" -> optional(text).verifying(radioInputSelected),
      "utr"     -> mandatoryIfEqual("variant", "utr", utr),
      "nino"    -> mandatoryIfEqual("variant", "nino", nino)
    )(RadioInvasiveTaxPayerOption.apply)(RadioInvasiveTaxPayerOption.unapply).verifying(
      "error.radio-variant.invalid",
      submittedTaxPayerOption =>
        ValidVariantsTaxPayerOptionForm.values.exists(_.toString == submittedTaxPayerOption.variant.getOrElse(""))
    ))

  val invasiveCheckStartSaAgentCode: Form[RadioInvasiveStartSaAgentCode] = Form[RadioInvasiveStartSaAgentCode](
    mapping(
      "hasSaAgentCode" -> optional(boolean).verifying(radioInputSelected),
      "saAgentCode"    -> mandatoryIfTrue("hasSaAgentCode", saAgentCode)
    )(RadioInvasiveStartSaAgentCode.apply)(RadioInvasiveStartSaAgentCode.unapply))
}
