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

package uk.gov.hmrc.agentsubscriptionfrontend

import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters._
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators._
import uk.gov.voa.play.form.ConditionalMappings.{mandatoryIfEqual, mandatoryIfTrue}

package object controllers {
  object BusinessIdentificationForms {
    val validBusinessTypes = Seq("sole_trader", "limited_company", "partnership", "llp")

    def knownFactsForm(businessType: String): Form[KnownFacts] =
      Form[KnownFacts](
        mapping("utr" -> businessUtr(businessType), "postcode" -> postcode)(
          (utrStr, postcode) =>
            normalizeUtr(utrStr)
              .map(utr => KnownFacts(utr, postcode))
              .getOrElse(throw new Exception("Invalid utr found after validation")))(knownFacts =>
          Some((knownFacts.utr.value, knownFacts.postcode))))

    val businessTypeForm: Form[BusinessType] =
      Form[BusinessType](
        mapping("businessType" -> optional(text).verifying(radioInputSelected("businessType.error.no-radio-selected")))(
          BusinessType.apply)(BusinessType.unapply)
          .verifying(
            "error.business-type-value.invalid",
            submittedBusinessType => validBusinessTypes.contains(submittedBusinessType.businessType.getOrElse(""))))

    val confirmBusinessForm: Form[ConfirmBusiness] =
      Form[ConfirmBusiness](
        mapping(
          "confirmBusiness" -> optional(text).verifying(radioInputSelected("confirmBusiness.error.no-radio-selected")))(
          answer => ConfirmBusiness(RadioInputAnswer.apply(answer.getOrElse(""))))(answer =>
          Some(RadioInputAnswer.unapply(answer.confirm)))
          .verifying(
            "error.confirm-business-value.invalid",
            submittedAnswer => Seq(Yes, No).contains(submittedAnswer.confirm)))

    val businessEmailForm = Form[BusinessEmail](
      mapping(
        "email" -> emailAddress
      )(BusinessEmail.apply)(BusinessEmail.unapply)
    )

    val businessNameForm = Form[BusinessName](
      mapping(
        "name" -> businessName
      )(BusinessName.apply)(BusinessName.unapply)
    )

    //uses variant "cannotProvide" to determine action if user cannot provide allowed options: utr or nino
    val clientDetailsForm: Form[RadioInvasiveTaxPayerOption] = Form[RadioInvasiveTaxPayerOption](
      mapping(
        "variant" -> optional(text).verifying(radioInputSelected("clientDetails.error.no-radio.selected")),
        "utr"     -> mandatoryIfEqual("variant", "utr", clientDetailsUtr),
        "nino"    -> mandatoryIfEqual("variant", "nino", clientDetailsNino)
      )(RadioInvasiveTaxPayerOption.apply)(RadioInvasiveTaxPayerOption.unapply).verifying(
        "error.radio-variant.invalid",
        submittedTaxPayerOption =>
          ValidVariantsTaxPayerOptionForm.values.exists(_.toString == submittedTaxPayerOption.variant.getOrElse(""))
      ))

    val invasiveCheckStartSaAgentCode: Form[RadioInvasiveStartSaAgentCode] = Form[RadioInvasiveStartSaAgentCode](
      mapping(
        "hasSaAgentCode" -> optional(boolean).verifying(radioInputSelected("invasive.error.no-radio.selected")),
        "saAgentCode"    -> mandatoryIfTrue("hasSaAgentCode", saAgentCode)
      )(RadioInvasiveStartSaAgentCode.apply)(RadioInvasiveStartSaAgentCode.unapply))
  }

  object SubscriptionControllerForms {
    val linkClientsForm: Form[LinkClients] =
      Form[LinkClients](
        mapping("autoMapping" -> optional(text).verifying(radioInputSelected("linkClients.error.no-radio-selected")))(
          ans => LinkClients(RadioInputAnswer.apply(ans.getOrElse(""))))(lc =>
          Some(RadioInputAnswer.unapply(lc.autoMapping)))
          .verifying(
            "error.link-clients-value.invalid",
            submittedLinkClients => Seq(Yes, No).contains(submittedLinkClients.autoMapping)))
  }
}
