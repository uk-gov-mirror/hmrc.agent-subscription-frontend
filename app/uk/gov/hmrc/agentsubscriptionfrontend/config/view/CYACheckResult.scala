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

package uk.gov.hmrc.agentsubscriptionfrontend.config.view

import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, ContactEmailData, ContactTradingAddressData, ContactTradingNameData, Registration}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, SubscriptionJourneyRecord}

sealed trait CYACheckResult

case class PassWithMaybeAmls(
  taxpayerName: String,
  address: BusinessAddress,
  amlsData: Option[AmlsData],
  contactEmailAddress: String,
  contactTradingName: Option[String],
  contactTradingAddress: BusinessAddress
) extends CYACheckResult

case object FailedRegistration extends CYACheckResult

case object FailedContactEmail extends CYACheckResult

case object FailedContactTradingName extends CYACheckResult

case object FailedContactTradingAddress extends CYACheckResult

object CYACheckResult {

  def check(sjr: SubscriptionJourneyRecord): CYACheckResult =
    (sjr.businessDetails.registration, sjr.amlsData, sjr.contactEmailData, sjr.contactTradingNameData, sjr.contactTradingAddressData) match {

      case (Some(reg), _, Some(email), Some(tradingName), Some(tradingAddress)) =>
        checkContactDetails(email, tradingName, tradingAddress)(reg, sjr.amlsData)

      case (None, _, _, _, _) => FailedRegistration
      case (_, _, None, _, _) => FailedContactEmail
      case (_, _, _, None, _) => FailedContactTradingName
      case (_, _, _, _, None) => FailedContactTradingAddress
    }

  private def checkContactDetails(
    emailData: ContactEmailData,
    tradingNameData: ContactTradingNameData,
    tradingAddressData: ContactTradingAddressData)(reg: Registration, maybeAmls: Option[AmlsData]): CYACheckResult =
    if (emailData.contactEmail.isEmpty) FailedContactEmail
    else if (tradingNameData.hasTradingName && tradingNameData.contactTradingName.isEmpty)
      FailedContactTradingName
    else if (tradingAddressData.contactTradingAddress.isEmpty) FailedContactTradingAddress
    else
      PassWithMaybeAmls(
        reg.taxpayerName.getOrElse(""),
        reg.address,
        maybeAmls,
        emailData.contactEmail.getOrElse(""),
        tradingNameData.contactTradingName,
        tradingAddressData.contactTradingAddress.get
      )
}
