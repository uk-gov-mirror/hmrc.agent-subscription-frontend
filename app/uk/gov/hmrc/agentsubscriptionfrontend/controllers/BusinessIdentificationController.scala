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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request, _}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AuditService
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models
import uk.gov.hmrc.agentsubscriptionfrontend.models.AssuranceResults._
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidVariantsTaxPayerOptionForm._
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.FailureReason._
import uk.gov.hmrc.agentsubscriptionfrontend.models.ValidationResult.{Failure, Pass}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.validators.InitialDetailsValidator
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.agentsubscriptionfrontend.views.html.invasive_check_start
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessIdentificationController @Inject()(
  assuranceService: AssuranceService,
  override val authConnector: AuthConnector,
  val subscriptionService: SubscriptionService,
  override val sessionStoreService: SessionStoreService,
  continueUrlActions: ContinueUrlActions,
  val initialDetailsValidator: InitialDetailsValidator,
  auditService: AuditService)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionDataSupport
    with SessionBehaviour {

  import BusinessIdentificationForms._

  val showCreateNewAccount: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.create_new_account())
    }
  }

  def showBusinessDetailsForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      continueUrlActions.withMaybeContinueUrlCached {

        mark("Count-Subscription-BusinessDetails-Start")
        withValidBusinessType { businessType =>
          Ok(html.business_details(knownFactsForm(businessType.key), businessType))
        }
      }
    }
  }

  def submitBusinessDetailsForm(): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      withValidBusinessType { businessType =>
        knownFactsForm(businessType.key)
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.business_details(formWithErrors, businessType)),
            knownFacts =>
              if (Utr.isValid(knownFacts.utr.value)) {
                checkBusinessDetailsGivenValidForm(knownFacts).map { resultWithSession =>
                  val sessionData = request.session.data ++ resultWithSession.session.data.toSeq
                  resultWithSession.withSession(sessionData.toSeq: _*)
                }
              } else {
                mark("Count-Subscription-NoAgencyFound")
                Redirect(routes.BusinessIdentificationController.showNoMatchFound())
            }
          )
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

          result <- withCleanCreds(agent) {
                     subscriptionService
                       .completePartialSubscription(knownFacts.utr, knownFacts.postcode)
                       .map { _ =>
                         mark("Count-Subscription-PartialSubscriptionCompleted")
                         Redirect(routes.SubscriptionController.showSubscriptionComplete())
                       }
                   }
        } yield result
      case SubscriptionProcess(SubscriptionState.SubscribedAndEnrolled, _) => {
        mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
        Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())
      }
      case _ =>
        mark("Count-Subscription-NoAgencyFound")
        Redirect(routes.BusinessIdentificationController.showNoMatchFound())
    }

  val showNoMatchFound: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.no_match_found())
    }
  }

  val setupIncomplete: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.cannot_create_account())
    }
  }

  val showConfirmBusinessForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidBusinessType { businessType =>
        withKnownFactsResult { knownFactsResult =>
          Ok(
            html.confirm_business(
              confirmBusinessRadioForm = confirmBusinessForm,
              registrationName = knownFactsResult.taxpayerName,
              utr = TaxIdentifierFormatters.prettify(knownFactsResult.utr),
              businessAddress = knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
            ))
        }
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
                Ok(html.confirm_business(
                  confirmBusinessRadioForm = formWithErrors,
                  registrationName = knownFactsResult.taxpayerName,
                  utr = TaxIdentifierFormatters.prettify(knownFactsResult.utr),
                  businessAddress = knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
                ))
              }
            },
            validatedBusiness => {
              val response: Future[Result] = validatedBusiness.confirm match {
                case Yes =>
                  if (knownFactsResult.isSubscribedToAgentServices) {
                    mark("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
                    Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())
                  } else {
                    val initialDetails = InitialDetails(
                      knownFactsResult.utr,
                      knownFactsResult.postcode,
                      knownFactsResult.taxpayerName,
                      knownFactsResult.emailAddress,
                      knownFactsResult.address.getOrElse(throw new Exception("address object missing"))
                    )

                    lookupNextPage(initialDetails).map(Redirect)
                  }
                case No =>
                  withValidBusinessType { _ =>
                    Redirect(routes.BusinessIdentificationController.showBusinessDetailsForm())
                  }
              }
              response
            }
          )
      }
    }
  }

  private def lookupNextPage(initialDetails: InitialDetails)(implicit hc: HeaderCarrier): Future[Call] = {
    val redirectCall = initialDetailsValidator.validate(initialDetails) match {
      case Failure(responses) if responses.contains(InvalidBusinessName) =>
        routes.BusinessIdentificationController.showBusinessNameForm()
      case Failure(responses) if responses.exists(r => r == InvalidBusinessAddress || r == DisallowedPostcode) =>
        routes.BusinessIdentificationController.showUpdateBusinessAddressForm()
      case Failure(responses) if responses.contains(InvalidEmail) =>
        routes.BusinessIdentificationController.showBusinessEmailForm()
      case _ =>
        routes.AMLSController.showMoneyLaunderingComplianceForm()
    }
    cacheInitialDetailsAndRedirect(initialDetails)(redirectCall)
  }

  val showBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        Ok(
          html.business_email(
            details.email.fold(businessEmailForm)(email => businessEmailForm.fill(BusinessEmail(email))),
            hasInvalidEmail(details)))
      }
    }
  }

  val changeBusinessEmail: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService
        .cacheIsChangingAnswers(true)
        .map(_ => Redirect(routes.BusinessIdentificationController.showBusinessEmailForm().url))
    }
  }

  val submitBusinessEmailForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        businessEmailForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.business_email(formWithErrors, hasInvalidEmail(details))),
            validForm => updateInitialDetailsAndRedirect(details.copy(email = Some(validForm.email)))
          )
      }
    }
  }

  val showBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        Ok(html.business_name(businessNameForm.fill(BusinessName(details.name)), hasInvalidBusinessName(details)))
      }
    }
  }

  val changeBusinessName: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      sessionStoreService
        .cacheIsChangingAnswers(true)
        .map(_ => Redirect(routes.BusinessIdentificationController.showBusinessNameForm().url))
    }
  }

  val submitBusinessNameForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        businessNameForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.business_name(formWithErrors, hasInvalidBusinessName(details))),
            validForm => updateInitialDetailsAndRedirect(details.copy(name = validForm.name))
          )
      }
    }
  }

  private def updateInitialDetailsAndRedirect(updatedDetails: InitialDetails)(implicit hc: HeaderCarrier) = {

    val result = for {
      _               <- sessionStoreService.cacheInitialDetails(updatedDetails)
      changingAnswers <- sessionStoreService.fetchIsChangingAnswers
    } yield changingAnswers

    result.flatMap[Result] {
      case Some(true) =>
        sessionStoreService
          .cacheIsChangingAnswers(false)
          .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
      case _ => lookupNextPage(updatedDetails).map(Redirect)
    }
  }

  val showUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        Ok(
          html.update_business_address(
            updateBusinessAddressForm.fill(models.UpdateBusinessAddressForm(details.businessAddress))))
      }
    }
  }

  val submitUpdateBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { details =>
        updateBusinessAddressForm
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(html.update_business_address(formWithErrors)),
            validForm => {
              val updatedBusinessAddress = details.businessAddress.copy(
                addressLine1 = validForm.addressLine1,
                addressLine2 = validForm.addressLine2,
                addressLine3 = validForm.addressLine3,
                addressLine4 = validForm.addressLine4,
                postalCode = Some(validForm.postCode)
              )
              val updatedDetails = details.copy(businessAddress = updatedBusinessAddress)

              val redirectCall: Future[Result] =
                initialDetailsValidator.validatePostcode(Some(validForm.postCode)) match {
                  case Pass =>
                    lookupNextPage(updatedDetails).map(Redirect)
                  case Failure(_) =>
                    Redirect(routes.BusinessIdentificationController.showPostcodeNotAllowed())
                }

              sessionStoreService
                .cacheInitialDetails(updatedDetails)
                .flatMap(_ => redirectCall)
            }
          )
      }
    }
  }

  val showPostcodeNotAllowed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withInitialDetails { _ =>
        Ok(html.postcode_not_allowed())
      }
    }
  }

  val showAlreadySubscribed: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.already_subscribed())
    }
  }

  def invasiveCheckStart: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(invasive_check_start(invasiveCheckStartSaAgentCode))
    }
  }

  def invasiveSaAgentCodePost: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      invasiveCheckStartSaAgentCode
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Ok(invasive_check_start(formWithErrors))
          },
          correctForm => {
            correctForm.hasSaAgentCode
              .map {
                case true =>
                  val saAgentCode = correctForm.saAgentCode
                    .getOrElse(throw new IllegalStateException(
                      "Form validation should enforce saAgentCode is always defined if hasSaAgentCode is true"))
                  Redirect(routes.BusinessIdentificationController.showClientDetailsForm())
                    .withSession(request.session + ("saAgentReferenceToCheck" -> saAgentCode))

                case false =>
                  mark("Count-Subscription-InvasiveCheck-Declined")
                  Redirect(routes.StartController.showCannotCreateAccount())
              }
              .getOrElse(throw new IllegalStateException(
                "InvasiveCheck invasiveCheckStartSaAgentCode.hasSaAgentCode should always be defined"))
          }
        )
    }
  }

  def showClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      Ok(html.client_details(clientDetailsForm))
    }
  }

  def submitClientDetailsForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { implicit agent =>
      clientDetailsForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            if (formWithErrors.errors.exists(_.message == "error.radio-variant.invalid")) {
              Logger.warn("Selected invasive tax payer form variant was invalid")
              throw new BadRequestException("submitted form value did not contain valid tax payer option form variant")
            }
            Ok(html.client_details(formWithErrors))
          },
          correctForm => {
            val retrievedVariant = correctForm.variant
              .getOrElse(throw new IllegalStateException(
                "Form validation should return error when submitting unavailable variant"))

            def utr: Utr =
              correctForm.utr
                .flatMap(TaxIdentifierFormatters.normalizeUtr)
                .getOrElse(throw new Exception("utr should not be empty"))
            def nino: Nino =
              correctForm.nino
                .flatMap(TaxIdentifierFormatters.normalizeNino)
                .getOrElse(throw new Exception("nino should not be empty"))

            ValidVariantsTaxPayerOptionForm.withName(retrievedVariant) match {
              case UtrV if Utr.isValid(utr.value) => checkAndRedirect(utr, "utr")
              case NinoV                          => checkAndRedirect(nino, "nino")
              case _ =>
                mark("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
                Redirect(routes.StartController.showCannotCreateAccount())
            }
          }
        )
    }
  }

  private def checkAndRedirect(
    value: TaxIdentifier,
    taxIdentifierName: String)(implicit hc: HeaderCarrier, request: Request[AnyContent], agent: Agent): Future[Result] =
    request.session.get("saAgentReferenceToCheck") match {
      case Some(saAgentReference) =>
        assuranceService
          .checkActiveCesaRelationship(value, taxIdentifierName, SaAgentReference(saAgentReference.toUpperCase))
          .map {
            case true =>
              mark("Count-Subscription-InvasiveCheck-Success")
              Redirect(routes.BusinessIdentificationController.showConfirmBusinessForm())
            case false =>
              mark("Count-Subscription-InvasiveCheck-Failed")
              Redirect(routes.StartController.showCannotCreateAccount())
          }
      case None => Redirect(routes.BusinessIdentificationController.invasiveCheckStart())
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
            .getOrElse(())
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
        Redirect(routes.StartController.showCannotCreateAccount())
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
