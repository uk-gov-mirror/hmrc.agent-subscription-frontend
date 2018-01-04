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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import play.api.mvc._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.{ContinueUrlActions, routes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.passcode.authentication.PasscodeAuthentication
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext, TaxRegime}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class AgentRequest[A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)

trait AuthActions extends Actions with PasscodeAuthentication {
  protected type AsyncPlayUserRequest = AuthContext => AgentRequest[AnyContent] => Future[Result]

  val continueUrlActions: ContinueUrlActions

  private implicit def hc(implicit request: Request[_]): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

  def AuthorisedWithSubscribingAgentAsync(regime: TaxRegime = NoOpRegime)(body: AsyncPlayUserRequest)(implicit appConfig: AppConfig): Action[AnyContent] =
    AuthorisedFor(regime, pageVisibility = GGConfidence).async {
      implicit authContext =>
        implicit request =>
          withVerifiedPasscode {
            enrolments.flatMap { enrolls =>
              (for {
                isAgent <- isAgentAffinityGroup
                activatedEnrol <- checkActivatedEnrollment(enrolls)
              } yield (isAgent, activatedEnrol)).flatMap {
                case (true, true) => {
                  continueUrlActions.extractContinueUrl.map {
                    case Some(continueUrl) => Redirect(continueUrl.url)
                    case None => Redirect(appConfig.agentServicesAccountUrl)
                  }
                }
                case (true, false) => body(authContext)(AgentRequest(enrolls, request))
                case _ => Future successful redirectToNonAgentNextSteps
              }
            }
          }
    }

  protected def enrolments(implicit authContext: AuthContext, hc: HeaderCarrier): Future[List[Enrolment]] =
    authConnector.getEnrolments[List[Enrolment]](authContext)

  private def checkActivatedEnrollment(enrolls: List[Enrolment]): Future[Boolean] =
    enrolls.find(_.key equals "HMRC-AS-AGENT") match {
      case Some(enroll) if enroll.isActivated => Future successful true
      case _ => Future successful false
    }

  protected def isAgentAffinityGroup()(implicit authContext: AuthContext, hc: HeaderCarrier): Future[Boolean] =
    authConnector.getUserDetails(authContext).map { userDetailsResponse =>
      val affinityGroup = (userDetailsResponse.json \ "affinityGroup").as[String]
      affinityGroup == "Agent"
    }

  private def redirectToNonAgentNextSteps: Result =
    Redirect(routes.StartController.showNonAgentNextSteps())
}
