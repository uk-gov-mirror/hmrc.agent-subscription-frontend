package uk.gov.hmrc.agentsubscriptionfrontend.support

import java.time.LocalDate

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, BusinessDetails, RegDetails, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.{givenNoSubscriptionJourneyRecordExists, givenSubscriptionJourneyRecordExists}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub.givenAgentIsNotManuallyAssured

trait TestSetupNoJourneyRecord {
  givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
}

trait TestSetupWithCompleteJourneyRecord {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"),
    SubscriptionJourneyRecord(AuthProviderId("12345-credId"),
      None,
      BusinessDetails(SoleTrader,
        Utr("8699323569"),
        Postcode("GU95 5MT"),
        Some(Registration(Some("tax name"), true, true,
          BusinessAddress("line 1", Some("line 2"), Some("line 3"), Some("line 4"), Some("POST"), "GB"),
          Some("abc@xyz.com")))), Some(AmlsData(true, Some(false), Some("supervisory"), None, Some(RegDetails("memNumber", LocalDate.now())))),
      cleanCredsAuthProviderId = Some(AuthProviderId("1234-creds"))))

  givenAgentIsNotManuallyAssured("8699323569")
}

