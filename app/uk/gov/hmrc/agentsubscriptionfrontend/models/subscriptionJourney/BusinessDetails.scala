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

package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessType, CompanyRegistrationNumber, DateOfBirth, Postcode, Registration, VatDetails}
import uk.gov.hmrc.domain.Nino

/**
  * Information about the agent's business.  They must always provide a business type, UTR and postcode.
  * But other data points are only required for some business types and if certain conditions are NOT met
  * e.g.
  *   if they provide a NINO, they must provide date of birth
  *   if they are registered for vat, they must provide vat details
  * The record is created once we have the minimum business details
  */
case class BusinessDetails(
  businessType: BusinessType,
  utr: Utr, // CT or SA
  postcode: Postcode,
  registration: Option[Registration] = None,
  nino: Option[Nino] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // if NINO required
  registeredForVat: Option[Boolean] = None,
  vatDetails: Option[VatDetails] = None) // if registered for VAT

object BusinessDetails {
  implicit val format: OFormat[BusinessDetails] = Json.format
}
