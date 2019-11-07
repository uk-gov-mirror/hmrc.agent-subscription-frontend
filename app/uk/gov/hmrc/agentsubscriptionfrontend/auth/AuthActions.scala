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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import play.api.{Configuration, Logger}
import play.api.mvc.Results._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.auth.Agent.hasNonEmptyEnrolments
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{RedirectUrlActions, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AuthProviderId
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects

import scala.concurrent.{ExecutionContext, Future}

class Agent(
  private val enrolments: Set[Enrolment],
  private val maybeCredentials: Option[Credentials],
  val subscriptionJourneyRecord: Option[SubscriptionJourneyRecord],
  val authNino: Option[String]) {

  def hasIrPayeAgent: Option[Enrolment] = enrolments.find(e => e.key == "IR-PAYE-AGENT" && e.isActivated)
  def hasIrsaAgent: Option[Enrolment] = enrolments.find(e => e.key == "IR-SA-AGENT" && e.isActivated)

  def authProviderId: AuthProviderId = AuthProviderId(maybeCredentials.fold("unknown")(_.providerId))
  def authProviderType: String = "GovernmentGateway"

  def getMandatorySubscriptionRecord: SubscriptionJourneyRecord =
    subscriptionJourneyRecord.getOrElse(throw new RuntimeException("Expected Journey Record missing"))

  def getMandatoryAmlsData: AmlsData =
    getMandatorySubscriptionRecord.amlsData.getOrElse(throw new RuntimeException("No AMLS data found in record"))

  def cleanCredsFold[A](isDirty: => A)(isClean: => A): A =
    this match {
      case hasNonEmptyEnrolments(_) => isDirty
      case _                        => isClean
    }

  val maybeCleanCredsAuthProviderId: Option[AuthProviderId] =
    cleanCredsFold(None: Option[AuthProviderId])(Some(this.authProviderId))

  def withCleanCredsOrCreateNewAccount(cleanCredsBody: => Future[Result]): Future[Result] =
    this.cleanCredsFold(isDirty = toFuture(Redirect(routes.BusinessIdentificationController.showCreateNewAccount())))(
      isClean = cleanCredsBody)

  def withCleanCredsOrSignIn(cleanCredsBody: => Future[Result]): Future[Result] =
    this.cleanCredsFold(isDirty = toFuture(Redirect(routes.SubscriptionController.showSignInWithNewID())))(
      isClean = cleanCredsBody)

}

object Agent {

  object hasNonEmptyEnrolments {
    def unapply(agent: Agent): Option[Unit] =
      if (agent.enrolments.nonEmpty) Some(()) else None
  }
}

trait AuthActions extends AuthorisedFunctions with AuthRedirects with Monitoring {

  def redirectUrlActions: RedirectUrlActions
  def appConfig: AppConfig
  def config: Configuration

  def subscriptionJourneyService: SubscriptionJourneyService

  /**
    * For a user logged in as a subscribed agent (finished journey)
    * */
  def withSubscribedAgent[A](body: (Arn, Option[SubscriptionJourneyRecord]) => Future[Result])(
    implicit request: Request[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    authorised(Enrolment("HMRC-AS-AGENT") and AuthProviders(GovernmentGateway))
      .retrieve(authorisedEnrolments and credentials) {
        case enrolments ~ creds =>
          creds match {
            case Some(c) =>
              subscriptionJourneyService.getJourneyRecord(AuthProviderId(c.providerId)).flatMap { sjrOpt =>
                getArn(enrolments) match {
                  case Some(arn) =>
                    body(arn, sjrOpt)
                  case None =>
                    Logger.warn("could not find the Arn for the logged in agent to continue")
                    Future successful Forbidden
                }
              }
            case None =>
              Logger.warn("User does not have the correct credentials")
              Redirect(routes.SignedOutController.signOut())
          }
      }
      .recover {
        handleException
      }

  /**
    *  User is half way through a setup/onboarding journey
    * */
  def withSubscribingAgent[A](body: Agent => Future[Result])(
    implicit request: Request[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(allEnrolments and credentials and nino) {
        case enrolments ~ creds ~ mayBeNino =>
          if (isEnrolledForHmrcAsAgent(enrolments)) {
            redirectUrlActions.withMaybeRedirectUrl {
              case Some(redirectUrl) =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-ContinueUrl")
                Redirect(redirectUrl) // end of journey; back to calling service
              case None =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
                Redirect(appConfig.agentServicesAccountUrl) // dashboard
            }
          } else {
            val authProviderId = AuthProviderId(creds.fold("unknown")(_.providerId))
            subscriptionJourneyService
              .getJourneyRecord(authProviderId)
              .flatMap(maybeSjr => body(new Agent(enrolments.enrolments, creds, maybeSjr, mayBeNino)))
            // check what we should do when AuthProviderId not available!
          }
      }
      .recover {
        handleException
      }

  def withAuthenticatedUser[A](
    body: => Future[Result])(implicit request: Request[A], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway))(body)
      .recover {
        handleException
      }

  private def isEnrolledForHmrcAsAgent(enrolments: Enrolments): Boolean =
    enrolments.enrolments.find(_.key equals "HMRC-AS-AGENT").exists(_.isActivated)

  private def getArn(enrolments: Enrolments) =
    for {
      enrolment  <- enrolments.getEnrolment("HMRC-AS-AGENT")
      identifier <- enrolment.getIdentifier("AgentReferenceNumber")
    } yield Arn(identifier.value)

  private def handleException(implicit ec: ExecutionContext, request: Request[_]): PartialFunction[Throwable, Result] = {

    case _: UnsupportedAffinityGroup =>
      mark("Count-Subscription-NonAgent")
      Redirect(routes.StartController.showNotAgent())

    case _: NoActiveSession =>
      toGGLogin(s"${appConfig.agentSubscriptionFrontendBaseUrl}${request.uri}")

    case _: InsufficientEnrolments =>
      Logger.warn(s"Logged in user does not have required enrolments")
      Forbidden

    case _: UnsupportedAuthProvider =>
      Logger.warn("User is not logged in via  GovernmentGateway, signing out and redirecting")
      Redirect(routes.SignedOutController.signOut())
  }
}
