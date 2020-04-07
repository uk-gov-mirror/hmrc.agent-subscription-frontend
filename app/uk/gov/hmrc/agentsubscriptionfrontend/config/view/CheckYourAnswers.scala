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

import java.time.format.DateTimeFormatter

import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, UserMapping}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessAddress, PendingDetails, RegisteredDetails}

case class CheckYourAnswers(
  businessNameRow: AnswerRow,
  businessAddressRow: AnswerRow,
  maybeAmlsDataRow: Option[AnswerRow],
  contactEmailRow: AnswerRow,
  contactTradingNameRow: AnswerRow,
  contactTradingAddressRow: AnswerRow,
  maybeMappingClientNumberRow: Option[AnswerRow],
  maybeMappingGGIdsRow: Option[AnswerRow])

object CheckYourAnswers {

  def apply(
    registrationName: String,
    address: BusinessAddress,
    amlsData: Option[AmlsData],
    isManuallyAssured: Boolean,
    userMappings: List[UserMapping],
    continueId: Option[String],
    contactEmailAddress: String,
    contactTradingName: Option[String],
    contactTradingAddress: BusinessAddress,
    appConfig: AppConfig)(implicit messages: Messages): CheckYourAnswers =
    CheckYourAnswers(
      businessNameRow = makeBusinessNameRow(registrationName),
      businessAddressRow = makeBusinessAddressRow(address),
      maybeAmlsDataRow = if (isManuallyAssured) None else makeAmlsDataRow(amlsData),
      contactEmailRow = makeContactEmailRow(contactEmailAddress),
      contactTradingNameRow = makeContactTradingNameRow(contactTradingName),
      contactTradingAddressRow = makeContactTradingAddressRow(contactTradingAddress),
      maybeMappingClientNumberRow =
        if (userMappings.isEmpty)
          None
        else
          Some(
            AnswerRow(
              question = Messages("checkAnswers.userMapping.label"),
              answerLines = List(userMappings.map(_.count).sum.toString),
              changeLink = Some(
                Call(
                  "GET",
                  url = appConfig.agentMappingFrontendStartUrl(
                    continueId.getOrElse(throw new RuntimeException("no continueId found in record"))))),
              buttonText = Some(Messages("checkAnswers.addMore.button"))
            )),
      maybeMappingGGIdsRow =
        if (userMappings.isEmpty)
          None
        else
          Some(
            AnswerRow(
              question = Messages("checkAnswers.ggId.label"),
              answerLines = userMappings.map(u => Messages("checkAnswers.ggId.xs", u.ggTag)),
              changeLink = None,
              buttonText = None
            ))
    )

  private def makeAmlsDataRow(amlsData: Option[AmlsData])(implicit messages: Messages) =
    amlsData.map { data =>
      AnswerRow(
        question = amlsQuestion(data),
        answerLines = amlsAnswer(data),
        changeLink = Some(routes.AMLSController.changeAmlsDetails()),
        buttonText = Some(defaultButtonText)
      )
    }

  private def defaultButtonText(implicit messages: Messages) = Messages("checkAnswers.change.button")

  private def amlsAnswer(data: AmlsData): List[String] =
    data.amlsDetails match {
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
    }

  private def amlsQuestion(data: AmlsData)(implicit messages: Messages): String =
    data.amlsDetails match {
      case Some(amlsDetails) =>
        amlsDetails.details match {
          case Left(PendingDetails(appliedOn)) => Messages("checkAnswers.amlsDetails.pending.label")
          case Right(RegisteredDetails(membershipNumber, membershipExpiresOn)) =>
            Messages("checkAnswers.amlsDetails.label")
        }
      case None => throw new Exception("AMLS details incomplete")
    }

  private def makeContactEmailRow(emailAddress: String)(implicit messages: Messages) =
    AnswerRow(
      question = Messages("checkAnswers.contactEmailAddress.label"),
      answerLines = List(emailAddress),
      changeLink = Some(routes.ContactDetailsController.changeContactEmailAddress()),
      buttonText = Some(defaultButtonText)
    )

  private def makeContactTradingNameRow(contactTradingName: Option[String])(implicit messages: Messages) =
    AnswerRow(
      question = Messages("checkAnswers.contactTradingName.label"),
      answerLines = List(contactTradingName.getOrElse(Messages("checkAnswers.tradingName.none"))),
      changeLink = Some(routes.ContactDetailsController.changeTradingName()),
      buttonText = Some(defaultButtonText)
    )

  private def makeContactTradingAddressRow(address: BusinessAddress)(implicit messages: Messages) =
    AnswerRow(
      question = Messages("checkAnswers.contactTradingAddress.label"),
      answerLines = List(
        Some(address.addressLine1),
        address.addressLine2,
        address.addressLine3,
        address.addressLine4,
        address.postalCode).flatten,
      changeLink = Some(routes.ContactDetailsController.changeCheckMainTradingAddress()),
      buttonText = Some(defaultButtonText)
    )

  private def makeBusinessAddressRow(address: BusinessAddress)(implicit messages: Messages) =
    AnswerRow(
      question = Messages("checkAnswers.businessAddress.label"),
      answerLines = List(
        Some(address.addressLine1),
        address.addressLine2,
        address.addressLine3,
        address.addressLine4,
        address.postalCode).flatten,
      changeLink = None,
      buttonText = None
    )

  private def makeBusinessNameRow(registrationName: String)(implicit messages: Messages) =
    AnswerRow(
      question = Messages("checkAnswers.businessName.label"),
      answerLines = List(registrationName),
      changeLink = None,
      buttonText = None
    )

}

case class AnswerRow(question: String, answerLines: List[String], changeLink: Option[Call], buttonText: Option[String])
