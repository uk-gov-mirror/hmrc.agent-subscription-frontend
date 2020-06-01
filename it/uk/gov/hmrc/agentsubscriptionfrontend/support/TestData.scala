package uk.gov.hmrc.agentsubscriptionfrontend.support
import java.time.LocalDate

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord, UserMapping}
import uk.gov.hmrc.domain.{AgentCode, Nino}

object TestData {

  val validBusinessTypes: Seq[BusinessType] =
    Seq(BusinessType.SoleTrader, BusinessType.LimitedCompany, BusinessType.Partnership, BusinessType.Llp)

  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  val invalidPostcode = "11AAAA"
  val blacklistedPostcode = "AB10 1ZT"

  val utr = Utr("2000000000")
  val testPostcode = "AA1 1AA"
  val registrationName = "My Agency"
  val tradingName = "My Trading Name"
  val emailTooLong = "emailaddresstoolongxxxxxxxxxxxxxxxxxxxxxwaytoolong@emailaddressistoolongggggggggggggggg_________waytoolong.comxxxxxxxxxxxxxxxxxxxxxxx"
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  val tradingAddress = BusinessAddress(
    "TradingAddress1 A",
    Some("TradingAddress2 A"),
    Some("TradingAddress3 A"),
    Some("TradingAddress4 A"),
    Some("TT11TT"),
    "GB")

  val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  val agentSession: AgentSession =
    AgentSession(
      businessType = Some(SoleTrader),
      utr = Some(validUtr),
      postcode = Some(Postcode("bn13 1hn")),
      nino = Some(Nino("AE123456C")))

  val agentSessionForLimitedCompany: AgentSession = agentSession.copy(businessType = Some(LimitedCompany))

  val agentSessionForLimitedPartnership: AgentSession = agentSession.copy(businessType = Some(Llp))

  val testRegistration = Registration(Some(registrationName), isSubscribedToAgentServices = false, isSubscribedToETMP = false, businessAddress, Some("test@gmail.com"))

  val id = AuthProviderId("12345-credId")

  val record: SubscriptionJourneyRecord = TestData.minimalSubscriptionJourneyRecord(id)

  def minimalSubscriptionJourneyRecord(authProviderId: AuthProviderId) =
    SubscriptionJourneyRecord(
      authProviderId,
      businessDetails = BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode)))

  val couldBePartiallySubscribedJourneyRecord =
    SubscriptionJourneyRecord(
      id,
      businessDetails = BusinessDetails(LimitedCompany, validUtr, Postcode(validPostcode),
        registration = Some(Registration(
          Some(registrationName),
          isSubscribedToAgentServices = false,
          isSubscribedToETMP = true,
          businessAddress,
          Some("test@gmail.com"))),
        nino = None,
        companyRegistrationNumber = Some(CompanyRegistrationNumber("01234567")),
        dateOfBirth = None,
        registeredForVat = Some(false),
        vatDetails = None),
      continueId = Some("/continue"),
      amlsData = None,
      userMappings = List.empty[UserMapping],
      cleanCredsAuthProviderId = Some(id),
      contactEmailData = None,
      contactTradingAddressData = None,
      contactTradingNameData = None
    )

  def minimalSubscriptionJourneyRecordWithAmls(authProviderId: AuthProviderId) =
    SubscriptionJourneyRecord(
      authProviderId,
      businessDetails = BusinessDetails(SoleTrader, validUtr, Postcode(validPostcode)),
      amlsData = Some(AmlsData.registeredUserNoDataEntered))

  val completeJourneyRecordNoMappings = SubscriptionJourneyRecord(
    authProviderId = AuthProviderId("12345-credId"),
    continueId = None,
    businessDetails = BusinessDetails(SoleTrader,
      validUtr,
      Postcode(validPostcode),
      registration = Some(Registration(
          Some(registrationName),
          isSubscribedToAgentServices = true,
          isSubscribedToETMP = true,
          businessAddress,
          Some("test@gmail.com")))
    ),
    amlsData = Some(AmlsData(
        amlsRegistered = true,
        Some(false),
        Some(
          AmlsDetails(
            "supervisory",
            Right(RegisteredDetails("123456789", LocalDate.now().plusDays(10))))))),
    cleanCredsAuthProviderId = Some(id),
    contactEmailData = Some(ContactEmailData(false, Some("email@email.com"))),
    contactTradingNameData = Some(ContactTradingNameData(true, Some(tradingName))),
    contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress)))
  )

  val completeJourneyRecordWithMappings: SubscriptionJourneyRecord = completeJourneyRecordNoMappings
    .copy(
      userMappings = List(
        UserMapping(AuthProviderId("map-1"),Some(AgentCode("ACODE")), List.empty, 20, "1234"),
        UserMapping(AuthProviderId("map-2"),Some(AgentCode("BCODE")), List.empty, 20, "5678")
      )
  )

  def completeJourneyRecordWithMappingsAndNewTradingDetails(
                                                               tradingName: Option[String],
                                                               tradingAddress: Option[BusinessAddress]) =
    completeJourneyRecordWithMappings
    .copy(
      contactTradingNameData = Some(ContactTradingNameData(true, tradingName)),
      contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress)))
    )

  def completeJourneyRecordWithUpdatedBusinessName(newBusinessName: String): SubscriptionJourneyRecord =
    completeJourneyRecordNoMappings.copy(businessDetails = BusinessDetails(SoleTrader,
      validUtr,
      Postcode(validPostcode),
      Some(Registration(Some(newBusinessName), isSubscribedToAgentServices = true, isSubscribedToETMP = true,
        businessAddress,
        Some("test@gmail.com")))))

  def completeJourneyRecordWithUpdatedBusinessEmail(newBusinessEmail: String): SubscriptionJourneyRecord =
    completeJourneyRecordNoMappings.copy(businessDetails = BusinessDetails(SoleTrader,
      validUtr,
      Postcode(validPostcode),
      Some(Registration(Some(registrationName), isSubscribedToAgentServices = true, isSubscribedToETMP = true,
        businessAddress,
        Some(newBusinessEmail)))))

}
