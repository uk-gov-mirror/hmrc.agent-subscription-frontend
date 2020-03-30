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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContactDetailsForms._
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{BusinessEmail, ContactEmailCheck, ContactEmailData, RadioInputAnswer}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{contact_email_address, contact_email_check}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactDetailsController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService,
  val metrics: Metrics,
  val config: Configuration,
  val env: Environment,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  contactEmailCheckTemplate: contact_email_check,
  contactEmailAddressTemplate: contact_email_address
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showContactEmailCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatorySubscriptionRecord.businessDetails.registration
        .flatMap(_.emailAddress)
        .fold(
          Redirect(routes.StartController.start())
        )(businessEmail =>
          agent.getMandatorySubscriptionRecord.contactEmailData match {
            case Some(data) => {
              Ok(
                contactEmailCheckTemplate(
                  contactEmailCheckForm
                    .fill(ContactEmailCheck(RadioInputAnswer
                      .apply(RadioInputAnswer.apply(data.contactEmailCheck)))),
                  businessEmail))
            }
            case None => Ok(contactEmailCheckTemplate(contactEmailCheckForm, businessEmail))
        })
    }
  }

  def submitContactEmailCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      sjr.businessDetails.registration
        .flatMap(_.emailAddress)
        .fold(
          Future successful Redirect(routes.StartController.start())
        )(
          businessEmail =>
            contactEmailCheckForm.bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(contactEmailCheckTemplate(formWithErrors, businessEmail))
                },
                validForm => {
                  val updatedSjr = if (validForm.check == Yes) {
                    sjr.copy(contactEmailData = Some(ContactEmailData(true, Some(businessEmail))))
                  } else {
                    sjr.copy(contactEmailData = Some(ContactEmailData(true, None)))
                  }
                  val call: Call =
                    if (validForm.check == Yes) routes.TaskListController.showTaskList()
                    else
                      routes.ContactDetailsController.showContactEmailAddress()

                  subscriptionJourneyService.saveJourneyRecord(updatedSjr).map(_ => Redirect(call))
                }
            )
        )
    }
  }

  def showContactEmailAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        agent.getMandatorySubscriptionRecord.contactEmailData
          .fold(Redirect(routes.ContactDetailsController.showContactEmailCheck())) { contactEmailData =>
            contactEmailData.contactEmail match {
              case Some(email) =>
                Ok(
                  contactEmailAddressTemplate(
                    contactEmailAddressForm.fill(BusinessEmail(email)),
                    isChanging.getOrElse(false)
                  ))
              case None => Ok(contactEmailAddressTemplate(contactEmailAddressForm, isChanging.getOrElse(false)))
            }
          }
      }
    }
  }

  def submitContactEmailAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        contactEmailAddressForm.bindFromRequest
          .fold(
            formWithErrors => {
              Ok(contactEmailAddressTemplate(formWithErrors, isChanging.getOrElse(false)))
            },
            validForm => {
              subscriptionJourneyService
                .saveJourneyRecord(agent.getMandatorySubscriptionRecord
                  .copy(contactEmailData = Some(ContactEmailData(true, Some(validForm.email)))))
                .map(_ => Redirect(routes.TaskListController.showTaskList()))
            }
          )
      }
    }
  }

}
