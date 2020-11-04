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

import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import play.api.data.Forms.{mapping, text, tuple}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError, Mapping}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.DateOfBirthController._
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AgentSession, BusinessType, DateOfBirth}
import uk.gov.hmrc.agentsubscriptionfrontend.service.{AssuranceService, SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators.checkOneAtATime
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.date_of_birth
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class DateOfBirthController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val assuranceService: AssuranceService,
  val metrics: Metrics,
  val env: Environment,
  val config: Configuration,
  val sessionStoreService: SessionStoreService,
  val subscriptionService: SubscriptionService,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  dateOfBirthTemplate: date_of_birth)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  /**
    * In-case of SoleTrader or Partnerships, we should display NI and DOB pages based on if nino and dob exist or not.
    * We need to force users to go through these pages, hence the below checks
    */
  def showDateOfBirthForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        checkSessionStateAndBusinessType(existingSession, agent) { businessType =>
          if (businessType == Llp)
            existingSession.lastNameFromCid match {
              case Some(_) => Ok(dateOfBirthTemplate(getForm(existingSession.dateOfBirth), businessType))
              case None    => Redirect(routes.BusinessIdentificationController.showNoMatchFound())
            } else Ok(dateOfBirthTemplate(getForm(existingSession.dateOfBirth), businessType))
        }
      }
    }
  }

  def submitDateOfBirthForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (_, existingSession) =>
        checkSessionStateAndBusinessType(existingSession, agent) { businessType =>
          dateOfBirthForm
            .bindFromRequest()
            .fold(
              formWithErrors => Ok(dateOfBirthTemplate(formWithRefinedErrors(formWithErrors), businessType)),
              validDob => {
                if (existingSession.dateOfBirthFromCid.contains(validDob)) {
                  companiesHouseKnownFactCheck(existingSession) {
                    updateSessionAndRedirect(existingSession.copy(dateOfBirth = Some(validDob)))(
                      routes.VatDetailsController.showRegisteredForVatForm())
                  }
                } else {
                  Redirect(routes.BusinessIdentificationController.showNoMatchFound())
                }
              }
            )
        }
      }
    }
  }

  private def companiesHouseKnownFactCheck(agentSession: AgentSession)(f: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    agentSession.businessType match {
      case Some(bt) =>
        if (bt == Llp) {
          (agentSession.companyRegistrationNumber, agentSession.lastNameFromCid) match {
            case (Some(crn), Some(name)) =>
              subscriptionService
                .companiesHouseKnownFactCheck(crn, name)
                .flatMap(
                  checkResult =>
                    if (checkResult) f
                    else Redirect(routes.BusinessIdentificationController.showNoMatchFound()))

            case (None, Some(_)) => Redirect(routes.CompanyRegistrationController.showCompanyRegNumberForm())
            case _               => Redirect(routes.NationalInsuranceController.showNationalInsuranceNumberForm())
          }
        } else f
      case None => Future successful Redirect(routes.BusinessTypeController.showBusinessTypeForm())
    }

  private def getForm(dateOfBirth: Option[DateOfBirth]): Form[DateOfBirth] = dateOfBirth match {
    case Some(dob) => dateOfBirthForm.fill(dob)
    case None      => dateOfBirthForm
  }

  private def checkSessionStateAndBusinessType(agentSession: AgentSession, agent: Agent)(result: (BusinessType => Future[Result]))(
    implicit hc: HeaderCarrier): Future[Result] =
    agentSession.businessType match {
      case b @ (Some(SoleTrader | Partnership | Llp)) => {
        (agent.authNino, agentSession.nino, agentSession.dateOfBirthFromCid) match {
          case (None, _, _) if (!b.contains(Llp)) => Redirect(routes.VatDetailsController.showRegisteredForVatForm())
          case (_, Some(_), Some(_))              => result(b.get)
          case (_, None, _)                       => Redirect(routes.NationalInsuranceController.showNationalInsuranceNumberForm())
          case (_, _, None) =>
            if (b.get != Llp) //check this handling
              Redirect(routes.VatDetailsController.showRegisteredForVatForm())
            else Redirect(routes.BusinessIdentificationController.showNoMatchFound())
        }
      }
      case _ =>
        updateSessionAndRedirect(AgentSession())(routes.BusinessTypeController.showBusinessTypeForm())
    }
}

object DateOfBirthController {

  def dateOfBirthForm: Form[DateOfBirth] =
    Form[DateOfBirth](
      mapping("dob" -> dateOfBirth)(input => DateOfBirth(input))(dob => Some(dob.value))
    )

  def dateOfBirth: Mapping[LocalDate] =
    tuple(
      "year"  -> text.verifying("year", y => !y.trim.isEmpty || y.matches("^[0-9]{4}$")),
      "month" -> text.verifying("month", y => !y.trim.isEmpty || y.matches("^[0-9]{1,2}$")),
      "day"   -> text.verifying("day", d => !d.trim.isEmpty || d.matches("^[0-9]{1,2}$"))
    ).verifying(checkOneAtATime(Seq(tooOldConstraint, futureDateConstraint)))
      .transform(
        { case (y, m, d) => LocalDate.of(y.trim.toInt, m.trim.toInt, d.trim.toInt) },
        (date: LocalDate) => (date.getYear.toString, date.getMonthValue.toString, date.getDayOfMonth.toString)
      )

  private val tooOldConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] { data: (String, String, String) =>
    val (year, month, day) = data
    Try {
      val dob = LocalDate.of(year.toInt, month.toInt, day.toInt)
      if (dob.isBefore(LocalDate.of(1900, 1, 1)))
        Invalid(ValidationError("date-of-birth.must.be.later.than.1900"))
      else
        Valid
    } match {
      case Failure(_) => Invalid(ValidationError("date-of-birth.invalid"))
      case Success(p) => p
    }
  }

  private val futureDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] { data: (String, String, String) =>
    val (year, month, day) = data
    if (LocalDate.of(year.toInt, month.toInt, day.toInt).isAfter(LocalDate.now()))
      Invalid(ValidationError("date-of-birth.must.be.past"))
    else
      Valid
  }

  def formWithRefinedErrors(form: Form[DateOfBirth]): Form[DateOfBirth] = {

    val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

    val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

    dateFieldErrors match {
      case Nil => form
      case _ =>
        form.copy(errors = form.errors.map { error =>
          if (error.key.contains("dob")) {
            FormError(error.key, "", error.args)
          } else error
        }.toList :+ FormError(key = "dob", message = refinedMessage, args = Seq()))
    }
  }

  private val dateFields =
    (error: FormError) => error.key == "dob.day" || error.key == "dob.month" || error.key == "dob.year"

  private def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
    dateFieldErrors.map(_.key).map(k => "dob.".r.replaceFirstIn(k, "")).sorted match {
      case List("day", "month", "year") => Some("date-of-birth.empty")
      case List("day", "month")         => Some("date-of-birth.day-month.empty")
      case List("day", "year")          => Some("date-of-birth.day-year.empty")
      case List("day")                  => Some("date-of-birth.day.invalid")
      case List("month", "year")        => Some("date-of-birth.month-year.empty")
      case List("month")                => Some("date-of-birth.month.invalid")
      case List("year")                 => Some("date-of-birth.year.invalid")
      case _                            => None
    }

}
