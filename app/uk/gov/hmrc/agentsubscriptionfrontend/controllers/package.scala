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

package uk.gov.hmrc.agentsubscriptionfrontend

import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessDetails, RadioInputAnswer, _}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters.normalizeUtr
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators._
import uk.gov.hmrc.domain.Nino
import uk.gov.voa.play.form.ConditionalMappings.{mandatoryIfEqual, mandatoryIfTrue}

package object controllers {
  object BusinessIdentificationForms {

    private val businessTypes = List(
      BusinessType.LimitedCompany.key,
      BusinessType.Llp.key,
      BusinessType.Partnership.key,
      BusinessType.SoleTrader.key)

    val businessTypeForm: Form[BusinessType] =
      Form[BusinessType](
        mapping(
          "businessType" -> nonEmptyText
            .verifying(
              "businessType.error.invalid-choice",
              value => businessTypes.contains(value)
            ))(input => BusinessType(input))(bType => Some(bType.key))
      )

    def businessDetailsForm(businessType: String): Form[BusinessDetails] =
      Form[BusinessDetails](
        mapping("utr" -> businessUtr(businessType), "postcode" -> postcode)(
          (utrStr, postcode) =>
            normalizeUtr(utrStr)
              .map(utr => BusinessDetails(utr, postcode))
              .getOrElse(throw new Exception("Invalid utr found after validation")))(businessDetails =>
          Some((businessDetails.utr.value, businessDetails.postcode))))

    def utrForm(businessType: String): Form[Utr] =
      Form[Utr](
        mapping("utr" -> businessUtr(businessType))(input => Utr(input))(utr => Some(utr.value))
      )

    def postcodeForm: Form[Postcode] =
      Form[Postcode](
        mapping("postcode" -> postcode)(input => Postcode(input))(postcode => Some(postcode.value))
      )

    def ninoForm: Form[Nino] =
      Form[Nino](
        mapping("nino" -> clientDetailsNino)(input => Nino(input))(nino => Some(nino.value))
      )

    val confirmBusinessForm: Form[ConfirmBusiness] =
      Form[ConfirmBusiness](
        mapping("confirmBusiness" -> text
          .verifying("error.confirm-business-value.invalid", value => value == "yes" || value == "no"))(answer =>
          ConfirmBusiness(RadioInputAnswer.apply(answer)))(answer => RadioInputAnswer.unapply(answer.confirm)))

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

    val updateBusinessAddressForm = Form[UpdateBusinessAddressForm](
      mapping(
        "addressLine1" -> addressLine1,
        "addressLine2" -> addressLine234(lineNumber = 2),
        "addressLine3" -> addressLine234(lineNumber = 3),
        "addressLine4" -> addressLine234(lineNumber = 4),
        "postcode"     -> postcode
      )(UpdateBusinessAddressForm.apply)(UpdateBusinessAddressForm.unapply))

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

  object CompanyRegistrationForms {
    val crnForm: Form[CompanyRegistrationNumber] =
      Form[CompanyRegistrationNumber](
        mapping("crn" -> crn)(input => CompanyRegistrationNumber(input))(crn => Some(crn.value))
      )
  }

  object AMLSForms {

    def checkAmlsForm: Form[RadioInputAnswer] =
      Form[RadioInputAnswer](
        mapping("registeredAmls" -> optional(text)
          .verifying("error.check-amls-value.invalid", a => a.contains("yes") || a.contains("no")))(a =>
          RadioInputAnswer.apply(a.getOrElse("")))(a => Some(RadioInputAnswer.unapply(a))))

    def appliedForAmlsForm: Form[RadioInputAnswer] =
      Form[RadioInputAnswer](
        mapping("amlsAppliedFor" -> optional(text)
          .verifying("error.check-amlsAppliedFor-value.invalid", a => a.contains("yes") || a.contains("no")))(a =>
          RadioInputAnswer.apply(a.getOrElse("")))(a => Some(RadioInputAnswer.unapply(a))))

    def amlsForm(bodies: Set[String]): Form[AMLSForm] =
      Form[AMLSForm](
        mapping(
          "amlsCode"         -> amlsCode(bodies),
          "membershipNumber" -> membershipNumber,
          "expiry"           -> expiryDate
        )(AMLSForm.apply)(AMLSForm.unapply))

    def amlsPendingForm(bodies: Set[String]): Form[AmlsPendingForm] =
      Form[AmlsPendingForm](
        mapping(
          "amlsCode"  -> amlsCode(bodies),
          "appliedOn" -> appliedOnDate
        )(AmlsPendingForm.apply)(AmlsPendingForm.unapply))

    import play.api.data.{Form, FormError}

    def formWithRefinedErrors(form: Form[AMLSForm]): Form[AMLSForm] = {

      val expiry = "expiry"
      val dateFields =
        (error: FormError) =>
          error.key == s"$expiry.day" || error.key == s"$expiry.month" || error.key == s"$expiry.year"

      def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
        dateFieldErrors.map(_.key).map(k => "expiry.".r.replaceFirstIn(k, "")).sorted match {
          case List("day", "month", "year") => Some("error.moneyLaunderingCompliance.date.empty")
          case List("day", "month")         => Some("error.moneyLaunderingCompliance.day.month.empty")
          case List("day", "year")          => Some("error.moneyLaunderingCompliance.day.year.empty")
          case List("day")                  => Some("error.moneyLaunderingCompliance.day.empty")
          case List("month", "year")        => Some("error.moneyLaunderingCompliance.month.year.empty")
          case List("month")                => Some("error.moneyLaunderingCompliance.month.empty")
          case List("year")                 => Some("error.moneyLaunderingCompliance.year.empty")
          case _                            => None
        }

      val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

      val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

      dateFieldErrors match {
        case Nil => form
        case _ =>
          form.copy(errors = form.errors.map { error =>
            if (error.key.contains("expiry")) {
              FormError(error.key, "", error.args)
            } else error
          }.toList :+ FormError(key = "expiry", message = refinedMessage, args = Seq()))
      }
    }

    def amlsPendingDetailsFormWithRefinedErrors(form: Form[AmlsPendingForm]): Form[AmlsPendingForm] = {

      val appliedOn = "appliedOn"
      val dateFields =
        (error: FormError) =>
          error.key == s"$appliedOn.day" || error.key == s"$appliedOn.month" || error.key == s"$appliedOn.year"

      def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
        dateFieldErrors.map(_.key).map(k => s"$appliedOn.".r.replaceFirstIn(k, "")).sorted match {
          case List("day", "month", "year") => Some("error.amls.pending.appliedOn.date.empty")
          case List("day", "month")         => Some("error.amls.pending.appliedOn.day.month.empty")
          case List("day", "year")          => Some("error.amls.pending.appliedOn.day.year.empty")
          case List("day")                  => Some("error.amls.pending.appliedOn.day.empty")
          case List("month", "year")        => Some("error.amls.pending.appliedOn.month.year.empty")
          case List("month")                => Some("error.amls.pending.appliedOn.month.empty")
          case List("year")                 => Some("error.amls.pending.appliedOn.year.empty")
          case _                            => None
        }

      val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

      val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

      dateFieldErrors match {
        case Nil => form
        case _ =>
          form.copy(errors = form.errors.map { error =>
            if (error.key.contains(appliedOn)) {
              FormError(error.key, "", error.args)
            } else error
          }.toList :+ FormError(key = appliedOn, message = refinedMessage, args = Seq()))
      }
    }
  }

}
