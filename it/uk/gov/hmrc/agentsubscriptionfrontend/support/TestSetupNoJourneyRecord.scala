package uk.gov.hmrc.agentsubscriptionfrontend.support

import uk.gov.hmrc.agentsubscriptionfrontend.models.AuthProviderId
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.givenNoSubscriptionJourneyRecordExists

trait TestSetupNoJourneyRecord {
  givenNoSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"))
}

