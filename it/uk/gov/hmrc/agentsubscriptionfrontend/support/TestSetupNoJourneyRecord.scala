package uk.gov.hmrc.agentsubscriptionfrontend.support

import java.time.LocalDate

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.SsoStub

trait TestSetupNoJourneyRecord {
  SsoStub.givenWhitelistedDomainsExist
  givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
}

trait TestSetupWithCompleteJourneyRecord {
  givenSubscriptionJourneyRecordExists(
    AuthProviderId("12345-credId"),
    SubscriptionJourneyRecord(
    authProviderId = AuthProviderId("12345-credId"),
     continueId =  None,
      businessDetails = BusinessDetails(
        SoleTrader,
        Utr("8699323569"),
        Postcode("GU95 5MT"),
        Some(
          Registration(
            Some("tax name"),
            isSubscribedToAgentServices = true,
            isSubscribedToETMP = true,
            BusinessAddress("line 1", Some("line 2"), Some("line 3"), Some("line 4"), Some("POST"), "GB"),
            Some("abc@xyz.com")))
      ),
      amlsData = Some(
        AmlsData(
          amlsRegistered = true,
          Some(false),
          Some(AmlsDetails("supervisory", Right(RegisteredDetails("memNumber", LocalDate.now())))))),
      cleanCredsAuthProviderId = Some(AuthProviderId("1234-creds")),
      contactEmailData = Some(ContactEmailData(true, Some("abc@xyz.com"))),
      contactTradingNameData = Some(ContactTradingNameData(false, None)),
      contactTradingAddressData = Some(ContactTradingAddressData(
        true,
        Some(BusinessAddress(
          "line 1",
          Some("line 2"),
          Some("line 3"),
          Some("line 4"),
          Some("POST"),
          "GB"))))
    )
  )

  givenAgentIsNotManuallyAssured("8699323569")
}
