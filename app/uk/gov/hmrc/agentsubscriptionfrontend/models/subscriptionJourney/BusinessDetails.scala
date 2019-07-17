package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.DateOfBirth
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
                            utr: Option[Utr] = None, // CT or SA
                            postcode: Option[Postcode] = None,
                            registration: Option[Registration] = None,
                            nino: Option[Nino] = None,
                            companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
                            dateOfBirth: Option[DateOfBirth] = None, // if NINO required
                            registeredForVat: Option[Boolean] = None,
                            vatDetails: Option[VatDetails] = None) // if registered for VAT

object BusinessDetails {
  implicit val format: OFormat[BusinessDetails] = Json.format
}
