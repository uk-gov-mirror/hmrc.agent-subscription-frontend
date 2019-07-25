package uk.gov.hmrc.agentsubscriptionfrontend.support
import java.time.LocalDate

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, RegDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.domain.Nino

object TestData {

  val validBusinessTypes =
    Seq(BusinessType.SoleTrader, BusinessType.LimitedCompany, BusinessType.Partnership, BusinessType.Llp)

  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  val invalidPostcode = "11AAAA"
  val blacklistedPostcode = "AB10 1ZT"

  val utr = Utr("2000000000")
  val testPostcode = "AA1 1AA"
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  val agentSession: AgentSession =
    AgentSession(
      businessType = Some(SoleTrader),
      utr = Some(validUtr),
      postcode = Some(Postcode("bn13 1hn")),
      nino = Some(Nino("AE123456C")))

  val agentSessionForLimitedCompany: AgentSession = agentSession.copy(businessType = Some(LimitedCompany))

  val testRegistration = Registration(Some(registrationName), false, false, businessAddress, Some("test@gmail.com"))

  val id = AuthProviderId("12345-credId")

  val record = TestData.minimalSubscriptionJourneyRecord(id)

  def minimalSubscriptionJourneyRecord(authProviderId: AuthProviderId) =
    SubscriptionJourneyRecord(
      authProviderId,
      businessDetails = BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode)))

  def minimalSubscriptionJourneyRecordWithAmls(authProviderId: AuthProviderId) =
    SubscriptionJourneyRecord(
      authProviderId,
      businessDetails = BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode)),
      amlsData = Some(AmlsData.registeredUserNoDataEntered))

  val completeJourneyRecord = SubscriptionJourneyRecord(AuthProviderId("12345-credId"),
    None,
    BusinessDetails(SoleTrader,
      validUtr,
      Postcode(validPostcode),
      Some(Registration(Some(registrationName), true, true,
        businessAddress,
        Some("test@gmail.com")))), Some(AmlsData(true, Some(false), Some("supervisory"), None, Some(RegDetails("123456789", LocalDate.now().plusDays(10))))),
    cleanCredsAuthProviderId = Some(id))

}
