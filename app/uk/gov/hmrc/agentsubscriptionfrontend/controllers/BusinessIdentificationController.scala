/*
 * Copyright 2018 HM Revenue & Customs
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
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.{Agent, AuthActions}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AgentAssuranceConnector, AgentSubscriptionConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.invasive_check_start
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import play.api.data.Forms._
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidVariantsTaxPayerOptionForm._
import uk.gov.hmrc.agentsubscriptionfrontend.validators.InitialDetailsValidator
import uk.gov.hmrc.agentsubscriptionfrontend.validators.ValidationResult.FailureReason.{InvalidBusinessAddress, InvalidBusinessName, InvalidEmail}
import uk.gov.hmrc.agentsubscriptionfrontend.validators.ValidationResult.{Failure, Pass}

import scala.concurrent.Future

object BusinessIdentificationController {
  val validBusinessTypes = Seq("sole_trader", "limited_company", "partnership", "llp")

  val knownFactsForm: Form[KnownFacts] =
    Form[KnownFacts](
      mapping("utr" -> FieldMappings.utr, "postcode" -> FieldMappings.postcode)(
        (utrStr, postcode) =>
          FieldMappings
            .normalizeUtr(utrStr)
            .map(utr => KnownFacts(utr, postcode))
            .getOrElse(throw new Exception("Invalid utr found after validation")))(knownFacts =>
        Some((knownFacts.utr.value, knownFacts.postcode))))

  val businessTypeForm: Form[BusinessType] =
    Form[BusinessType](
      mapping("businessType" -> optional(text).verifying(FieldMappings.radioInputSelected(
        "businessType.error.no-radio-selected")))(BusinessType.apply)(BusinessType.unapply)
        .verifying(
          "error.business-type-value.invalid",
          submittedBusinessType => validBusinessTypes.contains(submittedBusinessType.businessType.getOrElse(""))))

  val confirmBusinessForm: Form[ConfirmBusiness] =
    Form[ConfirmBusiness](
      mapping("confirmBusiness" -> optional(text).verifying(
        FieldMappings.radioInputSelected("confirmBusiness.error.no-radio-selected")))(answer =>
        ConfirmBusiness(RadioInputAnswer.apply(answer.getOrElse(""))))(answer =>
        Some(RadioInputAnswer.unapply(answer.confirm)))
        .verifying(
          "error.confirm-business-value.invalid",
          submittedAnswer => Seq(Yes, No).contains(submittedAnswer.confirm)))

  val businessEmailForm = Form[BusinessEmail](
    mapping(
      "email" -> FieldMappings.emailAddress
    )(BusinessEmail.apply)(BusinessEmail.unapply)
  )

  val businessNameForm = Form[BusinessName](
    mapping(
      "name" -> FieldMappings.businessName
    )(BusinessName.apply)(BusinessName.unapply)
  )
}

@Singleton
class BusinessIdentificationController @Inject()(
  assuranceService: AssuranceService,
  val agentAssuranceConnector: AgentAssuranceConnector,
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  val agentSubscriptionConnector: AgentSubscriptionConnector,
  val subscriptionService: SubscriptionService,
  override val sessionStoreService: SessionStoreService,
  val continueUrlActions: ContinueUrlActions,
  val initialDetailsValidator: InitialDetailsValidator,
  auditService: AuditService,
  override implicit val appConfig: AppConfig,
  val metrics: Metrics)
    extends FrontendController with I18nSupport with AuthActions with SessionDataMissing with Monitoring {

  import continueUrlActions._
  import BusinessIdentificationController._

  val showCreateNewAccount: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.create_new_account())
    }
  }

  def showBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withMaybeContinueUrlCached {
        Future successful Ok(html.business_type(BusinessIdentificationController.businessTypeForm))
      }
    }
  }

  def submitBusinessTypeForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessTypeForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            if (formWithErrors.errors.exists(_.message == "error.business-type-value.invalid")) {
              Logger.warn("Select business-type form submitted with invalid identifier")
              throw new BadRequestException("submitted form value did not contain valid businessType identifier")
            }
            Future successful Ok(html.business_type(formWithErrors))
          },
          validatedBusinessType =>
            Future successful Redirect(
              routes.BusinessIdentificationController.showBusinessDetailsForm(validatedBusinessType.businessType))
        )
    }
  }

  def showBusinessDetailsForm(businessType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      //withMaybeContinueUrlCached because, Currently still needed as a user might be arriving from: trusts registration flow or gov.uk guidence page, make sure this is not the case anymore before removing
      withMaybeContinueUrlCached {
        businessType match {
          case Some(businessTypeIdentifier)
              if BusinessIdentificationController.validBusinessTypes.contains(businessTypeIdentifier) => {
            mark("Count-Subscription-BusinessDetails-Start")
            Future successful Ok(
              html.business_details(BusinessIdentificationController.knownFactsForm, businessTypeIdentifier))
          }
          case _ => {
            Logger.warn("businessTypeIdentifier was missing, redirect and obtain from showCheckBusinessType page")
            Future successful Redirect(routes.BusinessIdentificationController.showBusinessTypeForm())
          }
        }
      }
    }
  }

  def submitBusinessDetailsForm(businessType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      businessType match {
        case Some(businessTypeIdentifier) if validBusinessTypes.contains(businessTypeIdentifier) => {
          knownFactsForm
            .bindFromRequest()
            .fold(
              formWithErrors => Future successful Ok(html.business_details(formWithErrors, businessTypeIdentifier)),
              knownFacts =>
                checkBusinessDetailsGivenValidForm(knownFacts).map { resultWithSession =>
                  val sessionData = (request.session.data ++ resultWithSession.session.data.toSeq) + ("businessType" -> businessTypeIdentifier)
                  resultWithSession.withSession(sessionData.toSeq: _*)
              }
            )
        }
        case _ => Future successful Redirect(routes.BusinessIdentificationController.showBusinessTypeForm())
      }
    }
  }

  private def checkBusinessDetailsGivenValidForm(
    knownFacts: KnownFacts)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent): Future[Result] =
    subscriptionService.getSubscriptionStatus(knownFacts.utr, knownFacts.postcode).flatMap {
      case SubscriptionProcess(SubscriptionState.Unsubscribed, Some(registrationDetails)) =>
        processCheckBusinessStatus(
          knownFacts.utr,
          registrationDetails.taxpayerName.get,
          knownFacts,
          registrationDetails
        )
      case SubscriptionProcess(SubscriptionState.SubscribedButNotEnrolled, Some(reg)) =>
        for {
          _ <- sessionStoreService.cacheKnownFactsResult(
                KnownFactsResult(
                  knownFacts.utr,
                  knownFacts.postcode,
                  reg.taxpayerName.get,
                  reg.isSubscribedToAgentServices,
                  address = None,
                  emailAddress = None))

          result <- withCleanCreds {
                     subscriptionService
                       .completePartialSubscription(knownFacts.utr, knownFacts.postcode)
                       .map { arn =>
                         mark("Count-Subscription-PartialSubscriptionCompleted")
                         Redirect(routes.SubscriptionController.showSubscriptionComplete())
                           .withSession(request.session + ("arn" -> arn.value))
                       }
                   }
        } yield result
      case SubscriptionProcess(SubscriptionState.SubscribedAndEnrolled, _) => {
        mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
        Future successful Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())
      }
      case _ => {
        mark("Count-Subscription-NoAgencyFound")
        Future successful Redirect(routes.BusinessIdentificationController.showNoAgencyFound())
      }
    }

  val showNoAgencyFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.no_agency_found())
    }
  }

  val setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.cannot_create_account())
    }
  }

  val showConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withKnownFactsResult { knownFactsResult =>
        Future successful Ok(
          html.confirm_business(
            confirmBusinessRadioForm = confirmBusinessForm,
            registrationName = knownFactsResult.taxpayerName,
            utr = FieldMappings.prettify(knownFactsResult.utr),
            businessAddress = knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
          ))
      }
    }
  }

  val submitConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withKnownFactsResult { knownFactsResult =>
        confirmBusinessForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              if (formWithErrors.errors.exists(_.message == "error.confirm-business-value.invalid")) {
                throw new BadRequestException("Form submitted with strange input value")
              } else {
                Future.successful(Ok(html.confirm_business(
                  confirmBusinessRadioForm = formWithErrors,
                  registrationName = knownFactsResult.taxpayerName,
                  utr = FieldMappings.prettify(knownFactsResult.utr),
                  businessAddress = knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
                )))
              }
            },
            validatedBusiness => {
              val response = validatedBusiness.confirm match {
                case Yes =>
                  if (knownFactsResult.isSubscribedToAgentServices) {
                    mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
                    Future.successful(routes.BusinessIdentificationController.showAlreadySubscribed())
                  } else {
                    val initialDetails = InitialDetails(
                      knownFactsResult.utr,
                      knownFactsResult.postcode,
                      knownFactsResult.taxpayerName,
                      knownFactsResult.emailAddress,
                      knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
                    )

                    lookupNextPage(initialDetails)
                  }
                case No =>
                  Future.successful(routes.BusinessIdentificationController.submitBusinessDetailsForm(
                    request.session.get("businessType")))
              }

              response.map(Redirect(_))
            }
          )
      }
    }
  }

  private def lookupNextPage(initialDetails: InitialDetails)(implicit hc: HeaderCarrier) = {
    val redirectCall = initialDetailsValidator.validate(initialDetails) match {
      case Failure(responses) if responses.contains(InvalidBusinessName) =>
        routes.BusinessIdentificationController.showBusinessNameForm()
      case Failure(responses) if responses.contains(InvalidEmail) =>
        routes.BusinessIdentificationController.showBusinessEmailForm()
      case _ =>
        mark("Count-Subscription-CleanCreds-Start")
        routes.SubscriptionController.showCheckAnswers()
    }

    cacheInitialDetailsAndRedirect(initialDetails)(redirectCall)
  }

  val showBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        val form =
          if (details.email.nonEmpty)
            businessEmailForm.fill(BusinessEmail(details.email.get))
          else businessEmailForm

        Future.successful(Ok(html.business_email(form, hasInvalidEmail(details))))
      }
    }
  }

  val submitBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        businessEmailForm
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(Ok(html.business_email(formWithErrors, hasInvalidEmail(details)))),
            validForm => {
              val updatedDetails = details.copy(email = Some(validForm.email))
              sessionStoreService
                .cacheInitialDetails(updatedDetails)
                .flatMap(_ => lookupNextPage(updatedDetails).map(Redirect(_)))
            }
          )
      }
    }
  }

  val showBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        Future.successful(
          Ok(html.business_name(businessNameForm.fill(BusinessName(details.name)), hasInvalidBusinessName(details))))
      }
    }
  }

  val submitBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        businessNameForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future.successful(Ok(html.business_name(formWithErrors, hasInvalidBusinessName(details)))),
            validForm => {
              val updatedDetails = details.copy(name = validForm.name)
              sessionStoreService
                .cacheInitialDetails(updatedDetails)
                .flatMap(_ => lookupNextPage(updatedDetails).map(Redirect(_)))
            }
          )
      }
    }
  }

  val showAlreadySubscribed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future successful Ok(html.already_subscribed())
    }
  }

  def invasiveCheckStart: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(invasive_check_start(RadioWithInput.invasiveCheckStartSaAgentCode)))
    }
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      RadioWithInput.invasiveCheckStartSaAgentCode
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(Ok(invasive_check_start(formWithErrors)))
          },
          correctForm => {
            correctForm.hasSaAgentCode
              .map {
                case true => {
                  val saAgentCode = correctForm.saAgentCode
                    .getOrElse(throw new IllegalStateException(
                      "Form validation should enforce saAgentCode is always defined if hasSaAgentCode is true"))
                  Future successful Redirect(routes.BusinessIdentificationController.showClientDetailsForm())
                    .withSession(request.session + ("saAgentReferenceToCheck" -> saAgentCode))
                }
                case false => {
                  mark("Count-Subscription-InvasiveCheck-Declined")
                  Future successful Redirect(routes.StartController.showCannotCreateAccount())
                }
              }
              .getOrElse(throw new IllegalStateException(
                "InvasiveCheck invasiveCheckStartSaAgentCode.hasSaAgentCode should always be defined"))
          }
        )
    }
  }

  def showClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Future.successful(Ok(html.client_details(RadioWithInput.invasiveCheckTaxPayerOption)))
    }
  }

  def submitClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      RadioWithInput.invasiveCheckTaxPayerOption
        .bindFromRequest()
        .fold(
          formWithErrors => {
            if (formWithErrors.errors.exists(_.message == "error.radio-variant.invalid")) {
              Logger.warn("Selected invasive tax payer form variant was invalid")
              throw new BadRequestException("submitted form value did not contain valid tax payer option form variant")
            }
            Future successful Ok(html.client_details(formWithErrors))
          },
          correctForm => {
            val retrievedVariant = correctForm.variant
              .getOrElse(throw new IllegalStateException(
                "Form validation should return error when submitting unavailable variant"))

            ValidVariantsTaxPayerOptionForm.withName(retrievedVariant) match {
              case UtrV  => checkAndRedirect(FieldMappings.normalizeUtr(correctForm.utr.get).get, "utr")
              case NinoV => checkAndRedirect(FieldMappings.normalizeNino(correctForm.nino.get).get, "nino")
              case CannotProvideV => {
                mark("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
                Future successful Redirect(routes.StartController.showCannotCreateAccount())
              }
            }
          }
        )
    }
  }

  private def checkAndRedirect(
    value: TaxIdentifier,
    taxIdentifierName: String)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent) =
    request.session.get("saAgentReferenceToCheck") match {
      case Some(saAgentReference) =>
        assuranceService
          .checkActiveCesaRelationship(value, taxIdentifierName, SaAgentReference(saAgentReference))
          .map {
            case true =>
              mark("Count-Subscription-InvasiveCheck-Success")
              Redirect(routes.BusinessIdentificationController.showConfirmBusinessForm())
            case false =>
              mark("Count-Subscription-InvasiveCheck-Failed")
              Redirect(routes.StartController.showCannotCreateAccount())
          }
      case None => Future.successful(Redirect(routes.BusinessIdentificationController.invasiveCheckStart()))
    }

  private def withCleanCreds(f: => Future[Result])(implicit agent: Agent): Future[Result] =
    agent match {
      case hasNonEmptyEnrolments(_) =>
        Future successful Redirect(routes.BusinessIdentificationController.showCreateNewAccount())
      case _ => f
    }

  private def cacheKnownFactsAndAudit(
    maybeAssuranceResults: Option[AssuranceResults],
    taxpayerName: String,
    knownFacts: KnownFacts,
    registration: Registration)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent],
    agent: Agent): Future[Unit] = {

    val knownFactsResult =
      KnownFactsResult(
        knownFacts.utr,
        knownFacts.postcode,
        taxpayerName,
        registration.isSubscribedToAgentServices,
        Some(registration.address),
        registration.emailAddress)

    for {
      _ <- sessionStoreService.cacheKnownFactsResult(knownFactsResult)
      _ <- maybeAssuranceResults
            .map(auditService.sendAgentAssuranceAuditEvent(knownFactsResult, _))
            .getOrElse(Future.successful(()))
    } yield ()
  }

  private def processCheckBusinessStatus(
    utr: Utr,
    taxpayerName: String,
    knownFacts: KnownFacts,
    registration: Registration)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent],
    agent: Agent): Future[Result] =
    assuranceService.assureIsAgent(knownFacts.utr).flatMap {
      case RefuseToDealWith(_) =>
        Future.successful(Redirect(routes.StartController.showCannotCreateAccount()))
      case CheckedInvisibleAssuranceAndFailed(assuranceResults) =>
        cacheKnownFactsAndAudit(Some(assuranceResults), taxpayerName, knownFacts, registration).map { _ =>
          mark("Count-Subscription-InvasiveCheck-Start")
          Redirect(routes.BusinessIdentificationController.invasiveCheckStart())
        }
      case maybeAssured @ (None | ManuallyAssured(_) | CheckedInvisibleAssuranceAndPassed(_)) => {
        cacheKnownFactsAndAudit(maybeAssured, taxpayerName, knownFacts, registration)
          .map { _ =>
            mark("Count-Subscription-ConfirmBusiness-Success")
            Redirect(routes.BusinessIdentificationController.showConfirmBusinessForm())
          }
      }
    }

  private def cacheInitialDetailsAndRedirect(initialDetails: InitialDetails)(call: => Call)(
    implicit hc: HeaderCarrier): Future[Call] =
    sessionStoreService.cacheInitialDetails(initialDetails).map(_ => call)

  def hasInvalidBusinessName(details: InitialDetails): Boolean = initialDetailsValidator.validate(details) match {
    case Failure(responses) if responses.contains(InvalidBusinessName) => true
    case _                                                             => false
  }

  def hasInvalidEmail(details: InitialDetails): Boolean = initialDetailsValidator.validate(details) match {
    case Failure(responses) if responses.contains(InvalidEmail) => true
    case _                                                      => false
  }
}
