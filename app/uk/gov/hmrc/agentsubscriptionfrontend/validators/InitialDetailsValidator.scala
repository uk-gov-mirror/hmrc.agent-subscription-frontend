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

package uk.gov.hmrc.agentsubscriptionfrontend.validators

import javax.inject.{Inject, Singleton}
import play.api.data.validation.{Constraints, Valid, Invalid, ValidationResult => PlayValdationResult}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, InitialDetails, ValidationResult}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.FailureReason._

@Singleton
class InitialDetailsValidator @Inject()() {
  import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult._
  import CommonValidators._

  def validate(initialDetails: InitialDetails): ValidationResult = {
    val allValidations = Set(
      validateEmail(initialDetails.email),
      validateBusinessName(initialDetails.name),
      validateBusinessAddress(initialDetails.businessAddress))

    val reasons: Set[FailureReason] = allValidations.collect {
      case Failure(reasons) => reasons
    }.flatten

    if (reasons.nonEmpty) Failure(reasons) else Pass
  }

  private def validateEmail(email: Option[String]): ValidationResult =
    email.map(Constraints.emailAddress(_)) match {
      case Some(Valid) => Pass
      case _           => Failure(InvalidEmail)
    }

  private def validateBusinessName(businessName: String): ValidationResult =
    if (CommonValidators.businessName.constraints.map(_(businessName)).forall(_ == Valid))
      Pass
    else Failure(InvalidBusinessName)

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
