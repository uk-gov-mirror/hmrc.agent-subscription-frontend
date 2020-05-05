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
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.BusinessIdentificationForms.postcodeForm
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.LLPControllerForms.partnerTypeForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.PartnerType.{CorporatePartner, IndividualPartner}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{partner_postcode, partner_type, partner_utr_details}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

@Singleton
class LLPController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val metrics: Metrics,
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  val subscriptionJourneyService: SubscriptionJourneyService,
  val sessionStoreService: SessionStoreService,
  subscriptionService: SubscriptionService,
  mcc: MessagesControllerComponents,
  partnerTypeTemplate: partner_type,
  partnerUtrDetailsTemplate: partner_utr_details,
  partnerPostcodeTemplate: partner_postcode)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showPartnerTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        existingSession.partnerType match {
          case Some(pt) => Ok(partnerTypeTemplate(partnerTypeForm.fill(pt)))
          case None     => Ok(partnerTypeTemplate(partnerTypeForm))
        }
      }
    }
  }

  def submitPartnerTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      partnerTypeForm.bindFromRequest
        .fold(
          formWithErrors => Ok(partnerTypeTemplate(formWithErrors)),
          validChoice => {
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(existingSession) => {
                val redirect =
                  if (validChoice == IndividualPartner)
                    routes.NationalInsuranceController.showNationalInsuranceNumberForm()
                  else routes.LLPController.showPartnerUtrForm()
                updateSessionAndRedirect(existingSession.copy(partnerType = Some(validChoice)))(redirect)
              }
              case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        )
    }
  }

  def showPartnerUtrForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        val form = BusinessIdentificationForms.utrForm("llp-partner")
        existingSession.partnerType match {
          case Some(pt) =>
            if (pt == CorporatePartner) {
              existingSession.partnerUtr match {
                case Some(utr) => Ok(partnerUtrDetailsTemplate(form.fill(utr)))
                case None      => Ok(partnerUtrDetailsTemplate(form))
              }
            } else Redirect(routes.LLPController.showPartnerTypeForm())
          case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
        }
      }
    }
  }

  def submitPartnerUtrForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      BusinessIdentificationForms
        .utrForm("llp-partner")
        .bindFromRequest
        .fold(
          formWithErrors => Ok(partnerUtrDetailsTemplate(formWithErrors)),
          validUtr => {
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(existingSession) =>
                updateSessionAndRedirect(existingSession.copy(partnerUtr = Some(validUtr)))(
                  routes.LLPController.showPartnerPostcodeForm()
                )
              case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        )
    }
  }

  def showPartnerPostcodeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.partnerType match {
          case Some(pt) =>
            if (pt == CorporatePartner) {
              existingSession.partnerPostcode match {
                case Some(postcode) => Ok(partnerPostcodeTemplate(postcodeForm.fill(postcode)))
                case None           => Ok(partnerPostcodeTemplate(postcodeForm))
              }
            } else Redirect(routes.LLPController.showPartnerTypeForm())
          case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
        }
      }
    }
  }

  def submitPartnerPostcodeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      postcodeForm.bindFromRequest
        .fold(
          formWithErrors => Ok(partnerPostcodeTemplate(formWithErrors)),
          validPostcode => {
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(existingSession) =>
                val crn = existingSession.companyRegistrationNumber.get
                val name = existingSession.partnerName.get
                subscriptionService
                  .companiesHouseCheckName(crn, name)
                  .flatMap { result =>
                    println(s"result is $result")
                    updateSessionAndRedirect(existingSession.copy(partnerPostcode = Some(validPostcode)))(
                      routes.VatDetailsController.showRegisteredForVatForm()
                    )
                  }
              // call DES with utr and postcode to get registration
              // pull out the taxpayer name from registration
              // make call to CH to see if that name is in the list using the CRN in session.

              case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        )
    }
  }

}
