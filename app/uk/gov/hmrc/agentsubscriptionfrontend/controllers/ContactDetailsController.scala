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
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.ContactDetailsForms._
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html._
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
  addressLookUpConnector: AddressLookupFrontendConnector,
  mcc: MessagesControllerComponents,
  contactEmailCheckTemplate: contact_email_check,
  contactEmailAddressTemplate: contact_email_address,
  contactTradingNameCheckTemplate: contact_trading_name_check,
  contactTradingNameTemplate: contact_trading_name,
  contactTradingAddressCheckTemplate: contact_trading_address_check,
  addressFormWithErrorsTemplate: address_form_with_errors
)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

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
                  val useBusinessEmail = RadioInputAnswer.toBoolean(validForm.check)
                  val maybeBusinessEmail = if (useBusinessEmail) Some(businessEmail) else None

                  val (check, mayBeEmail): (Boolean, Option[String]) =
                    sjr.contactEmailData
                      .fold(useBusinessEmail, maybeBusinessEmail)(data =>
                        if (useBusinessEmail) (true, maybeBusinessEmail)
                        else (false, data.contactEmail))

                  val call: Call =
                    if (useBusinessEmail) routes.TaskListController.showTaskList()
                    else
                      routes.ContactDetailsController.showContactEmailAddress()

                  subscriptionJourneyService
                    .saveJourneyRecord(sjr.copy(contactEmailData = Some(ContactEmailData(check, mayBeEmail))))
                    .map(_ => Redirect(call))
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
              val sjr = agent.getMandatorySubscriptionRecord
              val emailData: Option[ContactEmailData] =
                sjr.contactEmailData
                  .map(data => ContactEmailData(data.contactEmailCheck, Some(validForm.email)))

              subscriptionJourneyService
                .saveJourneyRecord(sjr.copy(contactEmailData = emailData))
                .map(_ => Redirect(routes.TaskListController.showTaskList()))
            }
          )
      }
    }
  }

  def showTradingNameCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatorySubscriptionRecord.businessDetails.registration
        .flatMap(_.taxpayerName)
        .fold(
          Redirect(routes.StartController.start())
        )(businessName =>
          agent.getMandatorySubscriptionRecord.contactTradingNameData match {
            case Some(data) => {
              Ok(
                contactTradingNameCheckTemplate(
                  contactTradingNameCheckForm
                    .fill(ContactTradingNameCheck(RadioInputAnswer
                      .apply(RadioInputAnswer.apply(data.contactTradingNameCheck)))),
                  businessName))
            }
            case None => Ok(contactTradingNameCheckTemplate(contactTradingNameCheckForm, businessName))
        })
    }
  }

  def submitTradingNameCheck: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      sjr.businessDetails.registration
        .flatMap(_.taxpayerName)
        .fold(
          Future successful Redirect(routes.StartController.start())
        )(
          businessName =>
            contactTradingNameCheckForm.bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(contactTradingNameCheckTemplate(formWithErrors, businessName))
                },
                validForm => {
                  val hasTradingName = RadioInputAnswer.toBoolean(validForm.check)
                  val (check, maybeTradingName): (Boolean, Option[String]) =
                    sjr.contactTradingNameData.fold(hasTradingName, Option.empty[String])(data =>
                      if (hasTradingName) (true, data.contactTradingName)
                      else (false, None))

                  val call: Call =
                    if (hasTradingName) routes.ContactDetailsController.showTradingName
                    else
                      routes.TaskListController.showTaskList()

                  subscriptionJourneyService
                    .saveJourneyRecord(
                      sjr.copy(contactTradingNameData = Some(ContactTradingNameData(check, maybeTradingName))))
                    .map(_ => Redirect(call))
                }
            )
        )
    }
  }

  def showTradingName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        agent.getMandatorySubscriptionRecord.contactTradingNameData
          .fold(Redirect(routes.ContactDetailsController.showTradingNameCheck())) { contactTradingData =>
            contactTradingData.contactTradingName match {
              case Some(tradingName) =>
                Ok(
                  contactTradingNameTemplate(
                    contactTradingNameForm.fill(BusinessName(tradingName)),
                    isChanging.getOrElse(false)
                  ))
              case None => Ok(contactTradingNameTemplate(contactTradingNameForm, isChanging.getOrElse(false)))
            }
          }
      }
    }
  }

  def submitTradingName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchIsChangingAnswers.flatMap { isChanging =>
        contactTradingNameForm.bindFromRequest
          .fold(
            formWithErrors => {
              Ok(contactTradingNameTemplate(formWithErrors, isChanging.getOrElse(false)))
            },
            validForm => {
              subscriptionJourneyService
                .saveJourneyRecord(agent.getMandatorySubscriptionRecord
                  .copy(contactTradingNameData = Some(ContactTradingNameData(true, Some(validForm.name)))))
                .map(_ => Redirect(routes.TaskListController.showTaskList()))
            }
          )
      }
    }
  }

  def showCheckMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      agent.getMandatorySubscriptionRecord.businessDetails.registration
        .map(_.address)
        .fold(
          Redirect(routes.StartController.start())
        )(businessAddress =>
          agent.getMandatorySubscriptionRecord.contactTradingAddressData match {
            case Some(data) => {
              Ok(
                contactTradingAddressCheckTemplate(
                  contactTradingAddressCheckForm
                    .fill(ContactTradingAddressCheck(RadioInputAnswer
                      .apply(RadioInputAnswer.apply(data.check)))),
                  formatBusinessAddress(businessAddress)
                ))
            }
            case None =>
              Ok(
                contactTradingAddressCheckTemplate(
                  contactTradingAddressCheckForm,
                  formatBusinessAddress(businessAddress)))
        })
    }
  }

  def submitCheckMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      sjr.businessDetails.registration
        .map(_.address)
        .fold(
          Future successful Redirect(routes.StartController.start())
        )(
          businessAddress =>
            contactTradingAddressCheckForm.bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(contactTradingAddressCheckTemplate(formWithErrors, formatBusinessAddress(businessAddress)))
                },
                validForm => {
                  val updatedSjr = if (validForm.check == Yes) {
                    sjr.copy(contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))))
                  } else {
                    sjr.copy(contactTradingAddressData = Some(ContactTradingAddressData(false, None)))
                  }
                  val call: Call =
                    if (validForm.check == Yes) routes.TaskListController.showTaskList
                    else
                      routes.ContactDetailsController.showMainTradingAddress

                  subscriptionJourneyService.saveJourneyRecord(updatedSjr).map(_ => Redirect(call))
                }
            )
        )
    }
  }

  def showMainTradingAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      mark("Count-Subscription-AddressLookup-Start")
      addressLookUpConnector
        .initJourney(routes.ContactDetailsController.returnFromAddressLookup(), JourneyName)
        .map(Redirect(_))
    }
  }

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      val sjr = agent.getMandatorySubscriptionRecord
      sjr.businessDetails.utr match {
        case utr =>
          addressLookUpConnector.getAddressDetails(id).flatMap { address =>
            desAddressForm
              .bindAddressLookupFrontendAddress(utr, address)
              .fold(
                formWithErrors => Future successful Ok(addressFormWithErrorsTemplate(formWithErrors, true)),
                validDesAddress => {
                  mark("Count-Subscription-AddressLookup-Success")
                  val updatedSjr =
                    sjr.copy(contactTradingAddressData =
                      Some(ContactTradingAddressData(true, Some(BusinessAddress(validDesAddress)))))

                  for {
                    _    <- subscriptionJourneyService.saveJourneyRecord(updatedSjr)
                    goto <- Redirect(routes.TaskListController.showTaskList())
                  } yield goto
                }
              )
          }
      }
    }
  }

  private def formatBusinessAddress(address: BusinessAddress): List[String] =
    List(
      Some(address.addressLine1),
      address.addressLine2,
      address.addressLine3,
      address.addressLine4,
      address.postalCode).flatten

}
