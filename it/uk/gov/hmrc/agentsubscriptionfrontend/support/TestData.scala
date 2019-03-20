package uk.gov.hmrc.agentsubscriptionfrontend.support
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.domain.Nino

object TestData {

  val validBusinessTypes = Seq(BusinessType.SoleTrader, BusinessType.LimitedCompany, BusinessType.Partnership, BusinessType.Llp)

  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  val invalidPostcode = "11AAAA"
  val blacklistedPostcode = "AB10 1ZT"

  val utr = Utr("0123456789")
  val postcode = "AA11AA"
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA11AA"), "GB")

  val initialDetails =
    InitialDetails(
      utr,
      "AA11AA",
      "My Agency",
      Some("agency@example.com"),
      businessAddress
    )

  val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  val agentSession: AgentSession =
    AgentSession(businessType = Some(SoleTrader), utr = Some(Utr("abc")), postcode = Some(Postcode("bn13 1hn")), nino = Some(Nino("AE123456C")))

  val agentSessionForLimitedCompany: AgentSession = agentSession.copy(businessType = Some(LimitedCompany))

}
