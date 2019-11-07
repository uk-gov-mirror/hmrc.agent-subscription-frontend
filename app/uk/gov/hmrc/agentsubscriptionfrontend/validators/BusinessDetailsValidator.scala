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

package uk.gov.hmrc.agentsubscriptionfrontend.validators

import javax.inject.{Inject, Singleton}
import play.api.data.validation.{Constraints, Invalid, Valid, ValidationResult => PlayValdationResult}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.FailureReason._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, Registration, ValidationResult}

@Singleton
class BusinessDetailsValidator @Inject()(appConfig: AppConfig) {
  import CommonValidators._
  import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult._

  private val blacklistedPostcodes = appConfig.blacklistedPostcodes

  def validate(business: Option[Registration]): ValidationResult =
    business match {
      case Some(details) =>
        val allValidations = Set(
          validateEmail(details.emailAddress),
          validateBusinessName(details.taxpayerName),
          validatePostcode(details.address.postalCode),
          validateBusinessAddress(details.address)
        )

        val reasons: Set[FailureReason] = allValidations.collect {
          case Failure(failureReasons) => failureReasons
        }.flatten

        if (reasons.nonEmpty) Failure(reasons) else Pass
      case None => Failure(Set.empty[FailureReason])
    }

  def validatePostcode(postcodeOpt: Option[String]): ValidationResult =
    postcodeOpt
      .map { postcode =>
        val formattedPostcode = postcode.trim.toUpperCase()

        // Checks for BFPO postcodes. Those postcodes starts with either BF or BFPO
        val isBfpo =
          !(formattedPostcode.startsWith("BF") || formattedPostcode.startsWith("BFPO"))
        val isValid = validateBlacklist(formattedPostcode, blacklistedPostcodes)

        if (isValid && isBfpo) Pass
        else Failure(DisallowedPostcode)
      }
      .getOrElse(Pass)

  private def validateEmail(email: Option[String]): ValidationResult =
    email.fold[ValidationResult](Failure(InvalidEmail)) { e =>
      Constraints.emailAddress().apply(e) match {
        case Valid => Pass
        case _     => Failure(InvalidEmail)
      }
    }

  private def validateBusinessName(businessName: Option[String]): ValidationResult =
    businessName match {
      case Some(name) =>
        if (CommonValidators.businessName.constraints.map(_(name)).forall(_ == Valid))
          Pass
        else Failure(InvalidBusinessName)
      case None => Failure(InvalidBusinessName)
    }

  private def validateBusinessAddress(businessAddress: BusinessAddress): ValidationResult = {
    def maybeAddressLineValidator(addressLine: Option[String]): Seq[PlayValdationResult] =
      addressLine.map(line => addressLine1.constraints.map(_(line))).getOrElse(Seq(Valid))

    val addressLine1Validator = maybeAddressLineValidator(Some(businessAddress.addressLine1))
    val addressLine2Validator = maybeAddressLineValidator(businessAddress.addressLine2)
    val addressLine3Validator = maybeAddressLineValidator(businessAddress.addressLine3)
    val addressLine4Validator = maybeAddressLineValidator(businessAddress.addressLine4)

    val basicPostcodeValidator: Seq[PlayValdationResult] = businessAddress.postalCode
      .map(postcode => CommonValidators.postcode.constraints.map(_(postcode)))
      .getOrElse(Seq(Invalid("invalid postcode")))

    val validators: Seq[PlayValdationResult] = Seq(
      addressLine1Validator,
      addressLine2Validator,
      addressLine3Validator,
      addressLine4Validator,
      basicPostcodeValidator).flatten

    if (validators.forall(_ == Valid)) Pass else Failure(InvalidBusinessAddress)
  }
}
