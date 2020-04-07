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

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, OFormat}
import uk.gov.hmrc.agentsubscriptionfrontend.config.view.CYACheckResult
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, AuthProviderId, BusinessAddress, ContactEmailData, ContactTradingAddressData, ContactTradingNameData}

/**
  * A Mongo record which represents the user's current journey in setting up a new
  * MTD Agent Services account, with their existing relationships.
  *
  */
final case class SubscriptionJourneyRecord(
  authProviderId: AuthProviderId,
  continueId: Option[String] = None, // once allocated, should not be changed?
  businessDetails: BusinessDetails,
  amlsData: Option[AmlsData] = None,
  userMappings: List[UserMapping] = List.empty,
  mappingComplete: Boolean = false,
  cleanCredsAuthProviderId: Option[AuthProviderId] = None,
  lastModifiedDate: Option[LocalDateTime] = None,
  contactEmailData: Option[ContactEmailData] = None,
  contactTradingNameData: Option[ContactTradingNameData] = None,
  contactTradingAddressData: Option[ContactTradingAddressData] = None
)

object SubscriptionJourneyRecord {

  import MongoLocalDateTimeFormat._

  implicit val subscriptionJourneyFormat: OFormat[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").format[AuthProviderId] and
      (JsPath \ "continueId").formatNullable[String] and
      (JsPath \ "businessDetails").format[BusinessDetails] and
      (JsPath \ "amlsData").formatNullable[AmlsData] and
      (JsPath \ "userMappings").format[List[UserMapping]] and
      (JsPath \ "mappingComplete").format[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").formatNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").formatNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").formatNullable[ContactEmailData] and
      (JsPath \ "contactTradingNameData").formatNullable[ContactTradingNameData] and
      (JsPath \ "contactTradingAddressData")
        .formatNullable[ContactTradingAddressData])(
      SubscriptionJourneyRecord.apply,
      unlift(SubscriptionJourneyRecord.unapply))

  def fromAgentSession(
    agentSession: AgentSession,
    authProviderId: AuthProviderId,
    cleanCredsAuthProviderId: Option[AuthProviderId] = None): SubscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      authProviderId = authProviderId,
      continueId = Some(UUID.randomUUID().toString.replace("-", "")),
      businessDetails = BusinessDetails(
        businessType =
          agentSession.businessType.getOrElse(throw new RuntimeException("no business type found in agent session")),
        utr = agentSession.utr.getOrElse(throw new RuntimeException("no utr found in agent session")),
        postcode = agentSession.postcode.getOrElse(throw new RuntimeException("no postcode found in agent session")),
        registration = agentSession.registration,
        nino = agentSession.nino,
        companyRegistrationNumber = agentSession.companyRegistrationNumber,
        dateOfBirth = agentSession.dateOfBirth
      ),
      cleanCredsAuthProviderId = cleanCredsAuthProviderId
    )
}
