/*
 * Copyright 2019 HM Revenue & Customs
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

/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, PendingDetails, RegisteredDetails}

case class RegDetails(membershipNumber: String, membershipExpiresOn: LocalDate)

object RegDetails {
  implicit val format: OFormat[RegDetails] = Json.format[RegDetails]
}

case class PendingDate(appliedOn: LocalDate)

object PendingDate {
  implicit val format: OFormat[PendingDate] = Json.format[PendingDate]
}

case class AmlsData(
  amlsRegistered: Boolean,
  amlsAppliedFor: Option[Boolean],
  supervisoryBody: Option[String],
  pendingDetails: Option[PendingDate],
  registeredDetails: Option[RegDetails])

object AmlsData {

  val registeredUserNoDataEntered = AmlsData(amlsRegistered = true, None, None, None, None)
  val nonRegisteredUserNoDataEntered = AmlsData(amlsRegistered = false, None, None, None, None)

  implicit val localDateFormat = new Format[LocalDate] {
    override def reads(json: JsValue): JsResult[LocalDate] =
      json.validate[String].map(LocalDate.parse)
    override def writes(o: LocalDate): JsValue = Json.toJson(o.toString)
  }

  implicit val format: Format[AmlsData] = Json.format[AmlsData]

  //TODO: convert backend to use AmlsData instead of AmlsDetails
  def amlsDataToDetails(amlsData: Option[AmlsData]): Option[AMLSDetails] =
    amlsData match {
      case Some(AmlsData(_, _, Some(supervisoryBody), Some(pendingDetails), None)) =>
        Some(AMLSDetails(supervisoryBody, Left(PendingDetails(pendingDetails.appliedOn))))
      case Some(AmlsData(_, _, Some(supervisoryBody), None, Some(registeredDetails))) =>
        Some(
          AMLSDetails(
            supervisoryBody,
            Right(RegisteredDetails(registeredDetails.membershipNumber, registeredDetails.membershipExpiresOn))))
      case _ => None
    }
}
