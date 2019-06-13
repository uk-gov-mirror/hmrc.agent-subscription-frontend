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
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.{AddressLookupFrontendConnector, MappingConnector}
import uk.gov.hmrc.agentsubscriptionfrontend.form.DesAddressForm
import uk.gov.hmrc.agentsubscriptionfrontend.models.RadioInputAnswer.{No, Yes}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.service.{SessionStoreService, SubscriptionReturnedHttpError, SubscriptionService}
import uk.gov.hmrc.agentsubscriptionfrontend.support.TaxIdentifierFormatters
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubscriptionController @Inject()(
  override val authConnector: AuthConnector,
  subscriptionService: SubscriptionService,
  override val sessionStoreService: SessionStoreService,
  addressLookUpConnector: AddressLookupFrontendConnector,
  mappingConnector: MappingConnector,
  continueUrlActions: ContinueUrlActions)(
  implicit messagesApi: MessagesApi,
  override val appConfig: AppConfig,
  override val metrics: Metrics,
  override val ec: ExecutionContext)
    extends AgentSubscriptionBaseController(authConnector, continueUrlActions, appConfig) with SessionBehaviour {

  import SubscriptionControllerForms._

  private val JourneyName: String = appConfig.journeyName
  private val blacklistedPostCodes: Set[String] = appConfig.blacklistedPostcodes

  val desAddressForm = new DesAddressForm(Logger, blacklistedPostCodes)

  val showCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withCleanCreds(agent) {
        withValidSession { (_, existingSession) =>
          (existingSession.registration, existingSession.amlsDetails) match {
            case (Some(registration), Some(amlsDetails)) =>
              sessionStoreService
                .cacheGoBackUrl(routes.SubscriptionController.showCheckAnswers().url)
                .map { _ =>
                  Ok(
                    html.check_answers(
                      registrationName = registration.taxpayerName.getOrElse(""),
                      address = registration.address,
                      emailAddress = registration.emailAddress,
                      amlsDetails = amlsDetails
                    ))
                }

            case (None, _) => Redirect(routes.BusinessDetailsController.showBusinessDetailsForm())

            case (_, None) => Redirect(routes.AMLSController.showCheckAmlsPage())
          }
        }
      }
    }
  }

  val submitCheckAnswers: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { agent =>
      withCleanCreds(agent) {
        withValidSession { (_, existingSession) =>
          (existingSession.utr, existingSession.postcode, existingSession.registration, existingSession.amlsDetails) match {
            case (Some(utr), Some(postcode), Some(registration), Some(amlsDetails)) =>
              subscriptionService
                .subscribe(utr, postcode, registration, amlsDetails)
                .flatMap(redirectSubscriptionResponse(_, utr))

            case _ =>
              Logger(getClass).warn(s"Missing data in session, redirecting back to /business-type")
              Redirect(routes.BusinessTypeController.showBusinessTypeForm())
          }
        }
      }
    }
  }

  val showBusinessAddressForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        mark("Count-Subscription-AddressLookup-Start")
        addressLookUpConnector
          .initJourney(routes.SubscriptionController.returnFromAddressLookup(), JourneyName)
          .map(Redirect(_))
      }
    }
  }

  private def redirectSubscriptionResponse(either: Either[SubscriptionReturnedHttpError, (Arn, String)], utr: Utr)(
    implicit request: Request[AnyContent]): Future[Result] =
    either match {
      case Right((arn, nameFromDetails)) =>
        mark("Count-Subscription-Complete")
        completeMappingWhenAvailable(utr, completedPartialSub = false)

      case Left(SubscriptionReturnedHttpError(CONFLICT)) =>
        mark("Count-Subscription-AlreadySubscribed-APIResponse")
        Redirect(routes.BusinessIdentificationController.showAlreadySubscribed())

      case Left(SubscriptionReturnedHttpError(status)) =>
        mark("Count-Subscription-Failed")
        throw new HttpException("Subscription failed", status)
    }

  def returnFromAddressLookup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        existingSession.utr match {
          case Some(utr) =>
            addressLookUpConnector.getAddressDetails(id).flatMap { address =>
              desAddressForm
                .bindAddressLookupFrontendAddress(utr, address)
                .fold(
                  formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
                  validDesAddress => {
                    mark("Count-Subscription-AddressLookup-Success")
                    val updatedReg = existingSession.registration match {
                      case Some(reg) => reg.copy(address = BusinessAddress(validDesAddress))
                      case None =>
                        throw new IllegalStateException("expecting registration in the session, but not found") //TODO
                    }
                    sessionStoreService
                      .cacheAgentSession(existingSession.copy(registration = Some(updatedReg)))
                      .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
                  }
                )
            }
          case None => Redirect(routes.UtrController.showUtrForm())
        }
      }
    }
  }

  def submitModifiedAddress: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withValidSession { (_, existingSession) =>
        desAddressForm.form
          .bindFromRequest()
          .fold(
            formWithErrors => Future successful Ok(html.address_form_with_errors(formWithErrors)),
            validDesAddress => {
              val updatedReg = existingSession.registration match {
                case Some(reg) => reg.copy(address = BusinessAddress(validDesAddress))
                case None =>
                  throw new IllegalStateException("expecting registration in the session, but not found") //TODO
              }
              sessionStoreService
                .cacheAgentSession(existingSession.copy(registration = Some(updatedReg)))
                .map(_ => Redirect(routes.SubscriptionController.showCheckAnswers()))
            }
          )
      }
    }
  }

  val showLinkClients: Action[AnyContent] = Action.async { implicit request =>
    appConfig.autoMapAgentEnrolments match {
      case true =>
        withSubscribingAgent { _ =>
          withValidSession { (_, _) =>
            toFuture(Ok(html.link_clients(linkClientsForm)))
          }
        }
      case false => toFuture(InternalServerError)
    }
  }

  val submitLinkClients: Action[AnyContent] = Action.async { implicit request =>
    appConfig.autoMapAgentEnrolments match {
      case true =>
        withSubscribingAgent { _ =>
          withValidSession { (_, existingSession) =>
            (existingSession.utr, existingSession.postcode) match {
              case (Some(utr), Some(postcode)) =>
                linkClientsForm
                  .bindFromRequest()
                  .fold(
                    formWithErrors => Ok(html.link_clients(formWithErrors)),
                    validatedLinkClients => {
                      val isPartiallySubscribed = request.session.get("isPartiallySubscribed").contains("true")

                      validatedLinkClients.autoMapping match {
                        case Yes =>
                          if (isPartiallySubscribed) {
                            for {
                              _ <- subscriptionService.completePartialSubscription(utr, postcode)
                              _ = mark("Count-Subscription-PartialSubscriptionCompleted")
                              returnResult <- completeMappingWhenAvailable(utr, completedPartialSub = true)
                            } yield returnResult.withSession(request.session - "isPartiallySubscribed")
                          } else {
                            toFuture(Redirect(routes.SubscriptionController.showCheckAnswers())
                              .withSession(request.session + ("performAutoMapping" -> "true")))
                          }

                        case No =>
                          if (isPartiallySubscribed) {
                            subscriptionService
                              .completePartialSubscription(utr, postcode)
                              .map { _ =>
                                mark("Count-Subscription-PartialSubscriptionCompleted")
                                Redirect(routes.SubscriptionController.showSubscriptionComplete())
                                  .withSession(request.session - "isPartiallySubscribed")
                              }
                          } else {
                            toFuture(Redirect(routes.SubscriptionController.showCheckAnswers())
                              .withSession(request.session - "performAutoMapping"))
                          }
                      }
                    }
                  )
              case _ => Redirect(routes.BusinessTypeController.showBusinessTypeForm())
            }
          }
        }

      case false => toFuture(InternalServerError)
    }
  }

  val showSubscriptionComplete: Action[AnyContent] = Action.async { implicit request =>
    def recoverSessionStoreWithNone[T]: PartialFunction[Throwable, Option[T]] = {
      case NonFatal(ex) =>
        Logger(getClass).warn("Session store service failure", ex)
        None
    }

    withSubscribedAgent { arn =>
      withValidSession { (_, existingSession) =>
        existingSession.registration match {
          case Some(registration) => {
            val agencyName = registration.taxpayerName.getOrElse(
              throw new RuntimeException("agency name is missing from registration"))
            val agencyEmail = registration.emailAddress.getOrElse(
              throw new RuntimeException("agency email is missing from registration"))
            for {
              continueUrlOpt <- sessionStoreService.fetchContinueUrl.recover(recoverSessionStoreWithNone)
              _              <- sessionStoreService.remove()
            } yield {
              val continueUrl = continueUrlOpt.map(_.url).getOrElse(appConfig.agentServicesAccountUrl)
              val isUrlToASAccount = continueUrlOpt.isEmpty
              Ok(html.subscription_complete(continueUrl, isUrlToASAccount, arn.value, agencyName, agencyEmail))
            }
          }
          case _ => {
            Logger.warn("no registration details found in agent session")
            Redirect(routes.BusinessIdentificationController.showNoMatchFound())
          }
        }
      }
    }
  }

  private def completeMappingWhenAvailable(utr: Utr, completedPartialSub: Boolean = false)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier): Future[Result] = {

    val doMappingAnswer: Boolean = request.session.get("performAutoMapping").contains("true") || completedPartialSub

    for {
      _ <- {
        if (appConfig.autoMapAgentEnrolments && doMappingAnswer)
          mappingConnector.updatePreSubscriptionWithArn(utr)
        else {
          toFuture(())
        }
      }
    } yield Redirect(routes.SubscriptionController.showSubscriptionComplete())
  }
}
