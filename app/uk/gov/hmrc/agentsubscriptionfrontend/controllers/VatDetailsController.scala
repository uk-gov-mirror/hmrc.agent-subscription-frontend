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
import play.api.data.Forms.{mapping, of, optional, text, _}
import play.api.data.format.Formats._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, Mapping, _}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.VatDetailsController.{formWithRefinedErrors, registeredForVatForm, vatDetailsForm}
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.{LimitedCompany, Llp, Partnership, SoleTrader}
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionJourneyService, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.CommonValidators.{checkOneAtATime, radioInputSelected}
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.{registered_for_vat, vat_details}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class VatDetailsController @Inject()(
  val redirectUrlActions: RedirectUrlActions,
  val authConnector: AuthConnector,
  val metrics: Metrics,
  val config: Configuration,
  val env: Environment,
  val sessionStoreService: SessionStoreService,
  val subscriptionService: SubscriptionService,
  val subscriptionJourneyService: SubscriptionJourneyService,
  mcc: MessagesControllerComponents,
  registeredForVatTemplate: registered_for_vat,
  vatDetailsTemplate: vat_details)(implicit val appConfig: AppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc) with SessionBehaviour with AuthActions {

  def showRegisteredForVatForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      sessionStoreService.fetchAgentSession.flatMap {
        case Some(agentSession) =>
          withAAChecks(agent, agentSession) {
            (agentSession.businessType, agentSession.registeredForVat) match {
              case (Some(businessType), Some(registeredForVat)) =>
                val rfv = RegisteredForVat(RadioInputAnswer.apply(registeredForVat.toString))
                Ok(registeredForVatTemplate(registeredForVatForm.fill(rfv), getBackLink(agent, businessType)(agentSession)))
              case (Some(businessType), None) =>
                Ok(registeredForVatTemplate(registeredForVatForm, getBackLink(agent, businessType)(agentSession)))

              case _ => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        case None => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
      }
    }
  }

  /**
    * In-case of SoleTrader or Partnerships, we should display NI and DOB pages based on if nino and dob exist or not.
    * We need to force users to go through these pages, hence the below checks
    */
  private def withAAChecks(agent: Agent, existingSession: AgentSession)(result: Result): Result =
    existingSession.businessType match {
      case Some(SoleTrader) | Some(Partnership) =>
        (agent.authNino, existingSession.nino, existingSession.dateOfBirthFromCid) match {
          case (None, _, _)             => result
          case (Some(_), None, _)       => Redirect(routes.NationalInsuranceController.showNationalInsuranceNumberForm())
          case (Some(_), Some(_), None) => result
          case (_, _, Some(_)) =>
            existingSession.dateOfBirth match {
              case Some(_) => result
              case None    => Redirect(routes.DateOfBirthController.showDateOfBirthForm())
            }
        }
      case Some(Llp) => AAChecksForLlp(existingSession)(result)
      case _         => result
    }

  private def AAChecksForLlp(existingSession: AgentSession)(result: Result): Result =
    existingSession.ctUtrCheckResult match {
      case Some(true) => result
      case Some(false) =>
        (existingSession.nino, existingSession.dateOfBirth) match {
          case (Some(_), Some(_)) => result
          case (None, _)          => Redirect(routes.NationalInsuranceController.showNationalInsuranceNumberForm())
          case (_, None)          => Redirect(routes.DateOfBirthController.showDateOfBirthForm())
        }
      case None => Redirect(routes.CompanyRegistrationController.showCompanyRegNumberForm())
    }

  def submitRegisteredForVatForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withValidSession { (businessType, existingSession) =>
        registeredForVatForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(registeredForVatTemplate(formWithErrors, getBackLink(agent, businessType)(existingSession))),
            choice => {
              val nextPage = if (choice.confirm == Yes) {
                routes.VatDetailsController.showVatDetailsForm()
              } else {
                routes.BusinessIdentificationController.showConfirmBusinessForm()
              }

              updateSessionAndRedirect(existingSession.copy(registeredForVat = Some(choice.confirm.toString)))(nextPage)
            }
          )
      }
    }
  }

  def showVatDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.vatDetails match {
          case Some(vatDetails) =>
            Ok(vatDetailsTemplate(vatDetailsForm.fill(vatDetails)))
          case None => Ok(vatDetailsTemplate(vatDetailsForm))
        }
      }
    }
  }

  def submitVatDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        vatDetailsForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(vatDetailsTemplate(formWithRefinedErrors(formWithErrors))),
            validForm => {
              subscriptionService.matchVatKnownFacts(validForm.vrn, validForm.regDate).flatMap { foundMatch =>
                if (foundMatch)
                  updateSessionAndRedirect(existingSession.copy(vatDetails = Some(validForm)))(
                    routes.BusinessIdentificationController.showConfirmBusinessForm())
                else
                  Redirect(routes.BusinessIdentificationController.showNoMatchFound())
              }
            }
          )
      }
    }
  }

  private def getBackLink(agent: Agent, businessType: BusinessType)(agentSession: AgentSession) =
    businessType match {
      case SoleTrader | Partnership => {
        agent.authNino match {
          case Some(_) => routes.DateOfBirthController.showDateOfBirthForm().url
          case None    => routes.PostcodeController.showPostcodeForm().url
        }
      }
      case Llp => {
        if (agentSession.dateOfBirth.isDefined) routes.DateOfBirthController.showDateOfBirthForm().url
        else routes.CompanyRegistrationController.showCompanyRegNumberForm().url

      }
      case LimitedCompany => routes.CompanyRegistrationController.showCompanyRegNumberForm().url
    }

}

object VatDetailsController {

  val registeredForVatForm: Form[RegisteredForVat] =
    Form[RegisteredForVat](
      mapping("registeredForVat" -> optional(text).verifying(radioInputSelected("registered-for-vat.error.no-radio-selected")))(answer =>
        RegisteredForVat(RadioInputAnswer.apply(answer.getOrElse(""))))(answer => Some(RadioInputAnswer.unapply(answer.confirm)))
        .verifying("registered-for-vat.confirm-business-value.invalid", submittedAnswer => Seq(Yes, No).contains(submittedAnswer.confirm)))

  val normalizedText: Mapping[String] = of[String].transform(_.replaceAll("\\s", ""), identity)

  val vatDetailsForm: Form[VatDetails] =
    Form[VatDetails](
      mapping(
        "vrn"     -> normalizedText.verifying(validVrn),
        "regDate" -> validRegDate
      ) { case (a, b) => VatDetails(Vrn(a), b) } { vatDetails =>
        Some((vatDetails.vrn.value, vatDetails.regDate))
      }
    )

  def nonEmpty(failure: String): Constraint[String] = Constraint[String] { fieldValue: String =>
    if (fieldValue.trim.isEmpty) Invalid(ValidationError(failure)) else Valid
  }

  def validVrn: Constraint[String] = Constraint[String] { fieldValue: String =>
    nonEmpty("vat-details.vrn.required")(fieldValue) match {
      case i: Invalid => i
      case Valid =>
        if (!Vrn.isValid(fieldValue))
          Invalid(ValidationError("vat-details.vrn.regex-failure"))
        else
          Valid
    }
  }

  def validRegDate: Mapping[LocalDate] =
    tuple(
      "year"  -> text.verifying("year", y => !y.trim.isEmpty || y.matches("^[0-9]{4}$")),
      "month" -> text.verifying("month", y => !y.trim.isEmpty || y.matches("^[0-9]{1,2}$")),
      "day"   -> text.verifying("day", d => !d.trim.isEmpty || d.matches("^[0-9]{1,2}$"))
    ).verifying(checkOneAtATime(Seq(realDateConstraint, futureDateConstraint)))
      .transform(
        { case (y, m, d) => LocalDate.of(y.trim.toInt, m.trim.toInt, d.trim.toInt) },
        (date: LocalDate) => (date.getYear.toString, date.getMonthValue.toString, date.getDayOfMonth.toString)
      )

  private def realDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] { data: (String, String, String) =>
    val (year, month, day) = data
    Try {
      val date = LocalDate.of(year.toInt, month.toInt, day.toInt)
      if (date.isBefore(LocalDate.of(1900, 1, 1)))
        Invalid(ValidationError("vat-details.regDate.must.be.later.than.1900"))
      else
        Valid
    } match {
      case Failure(_) => Invalid(ValidationError("vat-details.regDate.invalid"))
      case Success(p) => p
    }
  }

  private def futureDateConstraint: Constraint[(String, String, String)] = Constraint[(String, String, String)] { data: (String, String, String) =>
    val (year, month, day) = data
    Try {
      if (LocalDate.of(year.toInt, month.toInt, day.toInt).isAfter(LocalDate.now()))
        Invalid(ValidationError("vat-details.regDate.must.be.in.past"))
      else
        Valid
    } match {
      case Failure(_) => Invalid(ValidationError("vat-details.regDate.invalid"))
      case Success(p) => p
    }
  }

  def formWithRefinedErrors(form: Form[VatDetails]): Form[VatDetails] = {

    val dateFieldErrors: Seq[FormError] = form.errors.filter(dateFields)

    val refinedMessage = refineErrors(dateFieldErrors).getOrElse("")

    dateFieldErrors match {
      case Nil => form
      case _ =>
        form.copy(errors = form.errors.map { error =>
          if (error.key.contains("regDate")) {
            FormError(error.key, "", error.args)
          } else error
        }.toList :+ FormError(key = "regDate", message = refinedMessage, args = Seq()))
    }
  }

  private val dateFields =
    (error: FormError) => error.key == "regDate.day" || error.key == "regDate.month" || error.key == "regDate.year"

  private def refineErrors(dateFieldErrors: Seq[FormError]): Option[String] =
    dateFieldErrors.map(_.key).map(k => "regDate.".r.replaceFirstIn(k, "")).sorted match {
      case List("day", "month", "year") => Some("vat-details.regDate.required")
      case List("day", "month")         => Some("vat-details.regDate.day-month.empty")
      case List("day", "year")          => Some("vat-details.regDate.day-year.empty")
      case List("day")                  => Some("vat-details.regDate.day.invalid")
      case List("month", "year")        => Some("vat-details.regDate.month-year.empty")
      case List("month")                => Some("vat-details.regDate.month.invalid")
      case List("year")                 => Some("vat-details.regDate.year.invalid")
      case _                            => None
    }

}
