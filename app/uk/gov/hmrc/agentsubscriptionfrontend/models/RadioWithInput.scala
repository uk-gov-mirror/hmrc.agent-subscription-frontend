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

case class RadioInvasiveStartSaAgentCode(hasSaAgentCode: Option[Boolean], saAgentCode: Option[String])
case class RadioInvasiveTaxPayerOption(variant: Option[String], utr: Option[String], nino: Option[String])

sealed abstract class ValidVariantsTaxPayerOptionForm

object ValidVariantsTaxPayerOptionForm {
  def findByValue(value: String): ValidVariantsTaxPayerOptionForm = value match {
    case "utr"           => TaxPayerUtr
    case "nino"          => TaxPayerNino
    case "cannotProvide" => TaxPayerCannotProvide
  }
}

case object TaxPayerUtr extends ValidVariantsTaxPayerOptionForm
case object TaxPayerNino extends ValidVariantsTaxPayerOptionForm
case object TaxPayerCannotProvide extends ValidVariantsTaxPayerOptionForm
