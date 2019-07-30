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

package uk.gov.hmrc.agentsubscriptionfrontend.config.view

import java.time.format.DateTimeFormatter

import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, PendingDetails, RegisteredDetails}
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData

case class CheckYourAnswers(
  businessNameRow: AnswerRow,
  businessAddressRow: AnswerRow,
  businessEmailRow: AnswerRow,
  maybeAmlsDataRow: Option[AnswerRow])

object CheckYourAnswers {
  def apply(
    registrationName: String,
    address: BusinessAddress,
    emailAddress: Option[String],
    amlsData: Option[AmlsData])(implicit messages: Messages): CheckYourAnswers =
    CheckYourAnswers(
      businessNameRow = AnswerRow(
        question = Messages("checkAnswers.businessName.label"),
        answerLines = List(registrationName),
        changeLink = routes.BusinessIdentificationController.changeBusinessName()
      ),
      businessAddressRow = AnswerRow(
        question = Messages("checkAnswers.businessAddress.label"),
        answerLines = List(
          Some(address.addressLine1),
          address.addressLine2,
          address.addressLine3,
          address.addressLine4,
          address.postalCode).flatten,
        changeLink = routes.SubscriptionController.showBusinessAddressForm()
      ),
      businessEmailRow = AnswerRow(
        question = Messages("checkAnswers.businessEmailAddress.label"),
        answerLines = List(emailAddress).flatten,
        changeLink = routes.BusinessIdentificationController.changeBusinessEmail()
      ),
      maybeAmlsDataRow = amlsData.map { data =>
        AnswerRow(
          question = (data.amlsDetails) match {
            case Some(amlsDetails) =>
              amlsDetails.details match {
                case Left(PendingDetails(appliedOn)) => Messages("checkAnswers.amlsDetails.pending.label")
                case Right(RegisteredDetails(membershipNumber, membershipExpiresOn)) =>
                  Messages("checkAnswers.amlsDetails.label")
              }
            case None => throw new Exception("AMLS details incomplete")
          },
          answerLines = (data.amlsDetails) match {
            case Some(amlsDetails) =>
              amlsDetails.details match {
                case Left(PendingDetails(appliedOn)) =>
                  List(appliedOn.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                case Right(RegisteredDetails(membershipNumber, membershipExpiresOn)) =>
                  List(
                    amlsDetails.supervisoryBody,
                    membershipNumber,
                    membershipExpiresOn.format(DateTimeFormatter.ofPattern("dd MM yyyy"))
                  )
              }
            case None => throw new Exception("AMLS details incomplete")
          },
          changeLink = routes.AMLSController.changeAmlsDetails()
        )
      }
    )
}
case class AnswerRow(question: String, answerLines: List[String], changeLink: Call)
