/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.Registration
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class CheckAgencyController @Inject()
  (override val messagesApi: MessagesApi, override val authConnector: AuthConnector, val agentSubscriptionConnector: AgentSubscriptionConnector)
  (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with AuthActions {

  private val knownFactsForm = Form[KnownFacts](
    mapping(
      "utr" -> FieldMappings.utr,
      "postcode" -> FieldMappings.postcode
    )(KnownFacts.apply)(KnownFacts.unapply)
  )

  private[controllers] val showCheckAgencyStatusBody: AuthContext => (Request[AnyContent]) => Future[Result] = {
    implicit authContext =>
      implicit request =>
          Future successful Ok(html.check_agency_status(knownFactsForm))
  }

  val showAlreadySubscribed: Action[AnyContent] = AuthorisedWithSubscribingAgent { implicit authContext => implicit request =>
    Ok(html.already_subscribed())
  }

  val showHasOtherEnrolments: Action[AnyContent] = AuthorisedWithSubscribingAgent { implicit authContext => implicit request =>
    Ok(html.has_other_enrolments())
  }

  val showCheckAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        (hasMtdEnrolment, hasEnrolments) match {
          case (true, _) => Redirect(routes.CheckAgencyController.showAlreadySubscribed())
          case (_, true) => Redirect(routes.CheckAgencyController.showHasOtherEnrolments())
          case (false, false) => Ok(html.check_agency_status(knownFactsForm))
        }
  }

  private def hasMtdEnrolment(implicit request: AgentRequest[_]): Boolean = request.enrolments.exists(_.key == "HMRC-AS-AGENT")
  private def hasEnrolments(implicit request: AgentRequest[_]): Boolean = request.enrolments.nonEmpty

  val checkAgencyStatus: Action[AnyContent] = AuthorisedWithSubscribingAgentAsync { implicit authContext: AuthContext =>
    implicit request =>
        knownFactsForm.bindFromRequest().fold(
          formWithErrors => {
            Future successful Ok(html.check_agency_status(formWithErrors))
          },
          knownFacts => checkAgencyStatusGivenValidForm(knownFacts)
        )
  }

  private def checkAgencyStatusGivenValidForm(knownFacts: KnownFacts)
                                            (implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) map { maybeRegistration: Option[Registration] =>
      maybeRegistration match {
        case Some(Registration(Some(name), isSubscribedToAgentServices)) => Redirect(routes.CheckAgencyController.showConfirmYourAgency())
                          .flashing(
                            "registrationName" -> name,
                            "knownFactsPostcode" -> knownFacts.postcode,
                            "utr" -> knownFacts.utr,
                            "nextPage" -> nextPage(isSubscribedToAgentServices))
        case Some(_) => throw new IllegalStateException(s"The agency with UTR ${knownFacts.utr} has no organisation name.")
        case None => Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  private val alreadySubscribed = "alreadySubscribed"
  private val notSubscribed = "notSubscribed"

  private def nextPage(isSubscribedToAgentServices: Boolean) =
    if (isSubscribedToAgentServices) alreadySubscribed else notSubscribed

  val showNoAgencyFound: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
          Ok(html.no_agency_found())
  }

  private def lookupNextPageUrl(nextPage: String, utr: String, postcode: String): Option[String] =
    nextPage match {
      case `alreadySubscribed` => Some(routes.CheckAgencyController.showAlreadySubscribed().url)
      //TODO this should be the "Subscription Status-Not Subscribed page" page which is not yet implemented
      case `notSubscribed` => Some(routes.SubscriptionController.showSubscriptionDetails(utr = utr, knownFactsPostcode = postcode).url)
      case _ =>
        Logger.warn(s"""Unknown nextPage: "$nextPage"""")
        None
    }

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedWithSubscribingAgent {
    implicit authContext =>
      implicit request =>
        (for {
          postcode <- request.flash.data.get("knownFactsPostcode")
          utr <- request.flash.data.get("utr")
          nextPage <- request.flash.data.get("nextPage")
          nextPageUrl <- lookupNextPageUrl(nextPage, utr = utr, postcode = postcode)
        } yield {
          Ok(html.confirm_your_agency(
            registrationName = request.flash.data("registrationName"),
            postcode = postcode,
            utr = utr,
            nextPageUrl = nextPageUrl))
        }) getOrElse {
          Logger.warn("Missing flash scope or unknown nextPage, redirecting back to check-agency-status")
          Redirect(routes.CheckAgencyController.showCheckAgencyStatus())
        }
  }
}
