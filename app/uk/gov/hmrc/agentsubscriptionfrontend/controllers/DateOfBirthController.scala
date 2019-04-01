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

package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.data.Forms.{mapping, text, tuple}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.DateOfBirthController._
import uk.gov.hmrc.agentsubscriptionfrontend.models.DateOfBirth
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators.checkOneAtATime
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class DateOfBirthController @Inject()(
  override val continueUrlActions: ContinueUrlActions,
  override val authConnector: AuthConnector,
  val sessionStoreService: SessionStoreService)(
  implicit override val metrics: Metrics,
  override val appConfig: AppConfig,
  val ec: ExecutionContext,
  override val messagesApi: MessagesApi)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionDataSupport
    with SessionBehaviour {

  def showDateOfBirthForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(agentSession) =>
          agentSession.dateOfBirth match {
            case Some(dob) =>
              Ok(html.date_of_birth(dateOfBirthForm.fill(dob)))
            case None => Ok(html.date_of_birth(dateOfBirthForm))
          }
        case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
      }
    }
  }

  def submitDateOfBirthForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      dateOfBirthForm
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(html.date_of_birth(formWithRefinedErrors(formWithErrors))),
          validDob => {
            sessionStoreService.fetchAgentSession.flatMap {
              case Some(existingSession) =>
                updateSessionAndRedirect(existingSession.copy(dateOfBirth = Some(validDob)))(
                  routes.VatDetailsController.showRegisteredForVatForm())
              case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        )
    }
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

  private val tooOldConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] {
    data: (String, String, String) =>
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

  private val futureDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] {
    data: (String, String, String) =>
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
