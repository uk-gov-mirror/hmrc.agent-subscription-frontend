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

import play.api.data.Forms.{mapping, _}
import play.api.data.validation._
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{AuthActions, NoOpRegime}
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

  val showAlreadySubscribed: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence) { implicit authContext => implicit request =>
    Ok(html.already_subscribed())
  }

  val showHasOtherEnrolments: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence) { implicit authContext => implicit request =>
    Ok(html.has_other_enrolments())
  }

  val showCheckAgencyStatus: Action[AnyContent] = AuthorisedWithAgentAsync(showCheckAgencyStatusBody)

  val checkAgencyStatus: Action[AnyContent] = AuthorisedWithAgentAsync { implicit authContext: AuthContext =>
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
        case Some(_) => Redirect(routes.CheckAgencyController.showConfirmYourAgency())
        case None => Redirect(routes.CheckAgencyController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = AuthorisedWithAgent {
    implicit authContext =>
      implicit request =>
          Ok(html.no_agency_found())
  }

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedWithAgent {
    implicit authContext =>
      implicit request =>
          Ok(html.confirm_your_agency())
  }

}

object FieldMappings {
  private val utrConstraint = Constraints.pattern("^\\d{10}$".r, error = "error.utr.invalid")
  private val nonEmptyUtr: Constraint[String] = Constraint[String] { fieldValue: String =>
    Constraints.nonEmpty(fieldValue) match {
      case i: Invalid =>
        i
      case Valid =>
        utrConstraint(fieldValue)
    }
  }

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r
  private val nonEmptyPostcode: Constraint[String] = Constraint[String] { fieldValue: String =>
    Constraints.nonEmpty(fieldValue) match {
      case i: Invalid =>
        i
      case Valid =>
        val error = "error.postcode.invalid"
        val fieldValueWithoutSpaces = fieldValue.replace(" ", "")
        postcodeWithoutSpacesRegex.unapplySeq(fieldValueWithoutSpaces)
          .map(_ => Valid)
          .getOrElse(Invalid(ValidationError(error)))
    }
  }

  def utr: Mapping[String] = text verifying nonEmptyUtr
  def postcode: Mapping[String] = text verifying nonEmptyPostcode
}
