package uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AuthProviderId
import uk.gov.hmrc.domain.AgentCode

case class UserMapping(internalId: AuthProviderId, agentCodes: Seq[AgentCode] = Seq.empty, count: Int = 0)

object UserMapping {
  implicit val format: OFormat[UserMapping] = Json.format
}

