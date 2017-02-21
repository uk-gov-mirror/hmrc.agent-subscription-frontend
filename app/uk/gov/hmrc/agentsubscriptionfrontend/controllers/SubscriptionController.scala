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
import uk.gov.hmrc.agentsubscriptionfrontend.auth.NoOpRegime
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentSubscriptionConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.Registration
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class SubscriptionController @Inject()
  (override val messagesApi: MessagesApi, override val authConnector: AuthConnector, val agentSubscriptionConnector: AgentSubscriptionConnector)
  (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport with Actions {

  private val knownFactsForm = Form[KnownFacts](
    mapping(
      "utr" -> FieldMappings.utr,
      "postcode" -> FieldMappings.postcode
    )(KnownFacts.apply)(KnownFacts.unapply)
  )

  private[controllers] val showCheckAgencyStatusBody: (AuthContext) => (Request[AnyContent]) => Future[Result] = {
    implicit authContext =>
      implicit request =>
        ensureAffinityGroupIsAgent {
          Future successful Ok(html.check_agency_status(knownFactsForm))
        }
  }

  val showCheckAgencyStatus: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async(showCheckAgencyStatusBody)

  val showSubscriptionDetails: Action[AnyContent] = Action { implicit request =>
    Ok(html.subscription_details())
  }

  val checkAgencyStatus: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async { implicit authContext: AuthContext =>
    implicit request =>
      ensureAffinityGroupIsAgent {
        knownFactsForm.bindFromRequest().fold(
          formWithErrors => {
            Future successful Ok(html.check_agency_status(formWithErrors))
          },
          knownFacts => checkAgencyStatusGivenValidForm(knownFacts)
        )
      }
  }

  private def checkAgencyStatusGivenValidForm(knownFacts: KnownFacts)
                                            (implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    agentSubscriptionConnector.getRegistration(knownFacts.utr, knownFacts.postcode) map { maybeRegistration: Option[Registration] =>
      maybeRegistration match {
        case Some(_) => Redirect(routes.SubscriptionController.showConfirmYourAgency())
        case None => Redirect(routes.SubscriptionController.showNoAgencyFound())
      }
    }
  }

  val showNoAgencyFound: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async {
    implicit authContext =>
      implicit request =>
        ensureAffinityGroupIsAgent {
          Future successful Ok(html.no_agency_found())
        }
  }

  val showConfirmYourAgency: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async {
    implicit authContext =>
      implicit request =>
        ensureAffinityGroupIsAgent {
          Future successful Ok(html.confirm_your_agency())
        }
  }


  val submitSubscriptionDetails: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async {
    implicit authContext =>
      implicit request =>
        ensureAffinityGroupIsAgent {
          Future successful Redirect(routes.SubscriptionController.showSubscriptionComplete())
        }
  }

  val showSubscriptionComplete: Action[AnyContent] = AuthorisedFor(NoOpRegime, GGConfidence).async {
    implicit authContext =>
      implicit request =>
        ensureAffinityGroupIsAgent {
          Future successful Ok(html.subscription_complete())
        }
  }


  private def ensureAffinityGroupIsAgent(action: => Future[Result])(implicit authContext: AuthContext, hc: HeaderCarrier): Future[Result] =
    authConnector.getUserDetails(authContext).flatMap { userDetailsResponse =>
      val affinityGroup = (userDetailsResponse.json \ "affinityGroup").as[String]
      if (affinityGroup == "Agent") {
        action
      } else {
        Future successful redirectToNonAgentNextSteps
      }
    }

  private def redirectToNonAgentNextSteps =
    Redirect(routes.StartController.showNonAgentNextSteps())
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
