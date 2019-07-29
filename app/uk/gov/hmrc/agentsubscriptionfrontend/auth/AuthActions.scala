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

import play.api.mvc.Results._
import play.api.mvc.{Request, Result}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{ContinueUrlActions, routes}
import uk.gov.hmrc.agentsubscriptionfrontend.models.AuthProviderId
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.{AmlsData, SubscriptionJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.service.SubscriptionJourneyService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, authorisedEnrolments, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects

import scala.concurrent.{ExecutionContext, Future}

class Agent(
  private val enrolments: Set[Enrolment],
  private val maybeCredentials: Option[Credentials],
  val subscriptionJourneyRecord: Option[SubscriptionJourneyRecord]) {

  def hasIRPAYEAGENT: Option[Enrolment] = enrolments.find(e => e.key == "IR-PAYE-AGENT" && e.isActivated)
  def hasIRSAAGENT: Option[Enrolment] = enrolments.find(e => e.key == "IR-SA-AGENT" && e.isActivated)

  def authProviderId: AuthProviderId = AuthProviderId(maybeCredentials.fold("unknown")(_.providerId))
  def authProviderType: String = "GovernmentGateway"

  def getMandatorySubscriptionRecord: SubscriptionJourneyRecord =
    subscriptionJourneyRecord.getOrElse(throw new RuntimeException("Expected Journey Record missing"))

  def getMandatoryAmlsData: AmlsData =
    getMandatorySubscriptionRecord.amlsData.getOrElse(throw new RuntimeException("No AMLS data found in record"))

}

object Agent {

  object hasHmrcAsAgentEnrolment {
    def unapply(agent: Agent): Option[Unit] =
      if (agent.enrolments.exists(_.key == "HMRC-AS-AGENT")) Some(()) else None
  }

  object hasNonEmptyEnrolments {
    def unapply(agent: Agent): Option[Unit] =
      if (agent.enrolments.nonEmpty) Some(()) else None
  }
}

trait AuthActions extends AuthorisedFunctions with AuthRedirects with Monitoring {

  def continueUrlActions: ContinueUrlActions
  def appConfig: AppConfig

  def env: Environment = appConfig.environment
  def config: Configuration = appConfig.configuration

  def subscriptionJourneyService: SubscriptionJourneyService

  /**
    * For a user logged in as a subscribed agent (finished journey)
    * */
  def withSubscribedAgent[A](body: (Arn, SubscriptionJourneyRecord) => Future[Result])(
    implicit request: Request[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    authorised(Enrolment("HMRC-AS-AGENT") and AuthProviders(GovernmentGateway))
      .retrieve(authorisedEnrolments and credentials) {
        case enrolments ~ creds =>
          creds match {
            case Some(c) =>
              subscriptionJourneyService.getJourneyRecord(AuthProviderId(c.providerId)).flatMap {
                case Some(sjr) =>
                  body(
                    getEnrolmentValue(enrolments, "HMRC-AS-AGENT", "AgentReferenceNumber")
                      .map(Arn(_))
                      .getOrElse(
                        throw InsufficientEnrolments("could not find the HMRC-AS-AGENT enrolment to continue")),
                    sjr
                  )
                case None => throw new RuntimeException("subscription journey record expected")
              }
            case None => throw new UnsupportedCredentialRole("credentials expected but not found")
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
      .retrieve(allEnrolments and credentials) {
        case enrolments ~ creds =>
          if (isEnrolledForHmrcAsAgent(enrolments)) {
            continueUrlActions.extractContinueUrl.map {
              case Some(continueUrl) =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-ContinueUrl")
                Redirect(continueUrl.url) // end of journey; back to calling service
              case None =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
                Redirect(appConfig.agentServicesAccountUrl) // dashboard
            }
          } else {
            val authProviderId = AuthProviderId(creds.fold("unknown")(_.providerId))
            subscriptionJourneyService
              .getJourneyRecord(authProviderId)
              .flatMap(maybeSjr => body(new Agent(enrolments.enrolments, creds, maybeSjr)))
            // check what we should do when AuthProviderId not available!
          }
      }
      .recover {
        handleException
      }

  // only for the task list?
  //
  def withSubscribingOrSubscribedAgent[A](body: Agent => Future[Result])(
    implicit request: Request[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(allEnrolments and credentials) {
        case enrolments ~ maybeCredentials =>
          if (isEnrolledForHmrcAsAgent(enrolments)) {
            continueUrlActions.extractContinueUrl.flatMap {
              case Some(continueUrl) =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-ContinueUrl")
                Future successful Redirect(continueUrl.url)
              case None =>
                mark("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
                bodyWithJourneyRecord(enrolments, maybeCredentials)(body)
            }
          } else {
            bodyWithJourneyRecord(enrolments, maybeCredentials)(body)
          }
      }
      .recover {
        handleException
      }

  def bodyWithJourneyRecord(enrolments: Enrolments, maybeCredentials: Option[Credentials])(
    body: Agent => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    subscriptionJourneyService
      .getJourneyRecord(AuthProviderId(maybeCredentials.fold("unknown")(_.providerId)))
      .flatMap(maybeRecord => body(new Agent(enrolments.enrolments, maybeCredentials, maybeRecord)))

  def withAuthenticatedAgent[A](
    body: => Future[Result])(implicit request: Request[A], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)(body)
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

  private def getEnrolmentValue(enrolments: Enrolments, serviceName: String, identifierKey: String) =
    for {
      enrolment  <- enrolments.getEnrolment(serviceName)
      identifier <- enrolment.getIdentifier(identifierKey)
    } yield identifier.value

  private def handleException(implicit ec: ExecutionContext, request: Request[_]): PartialFunction[Throwable, Result] = {

    case _: UnsupportedAffinityGroup =>
      mark("Count-Subscription-NonAgent")
      Redirect(routes.StartController.showNotAgent())

    case _: NoActiveSession =>
      toGGLogin(if (appConfig.isDevMode) s"http://${request.host}${request.uri}" else s"${request.uri}")

    case _: UnsupportedAuthProvider =>
      Logger.warn("User is not logged in via  GovernmentGateway, signing out and redirecting")
      Redirect(routes.SignedOutController.signOut())

    case _: UnsupportedCredentialRole =>
      Logger.warn("User does not have the correct credentials")
      Redirect(routes.SignedOutController.signOut())
  }
}
