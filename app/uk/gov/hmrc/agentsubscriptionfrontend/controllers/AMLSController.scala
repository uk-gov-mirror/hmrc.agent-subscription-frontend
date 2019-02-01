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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, _}
import uk.gov.hmrc.agentsubscriptionfrontend.auth.AuthActions
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader
import uk.gov.hmrc.agentsubscriptionfrontend.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentsubscriptionfrontend.models.AMLSDetails
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.Monitoring
import uk.gov.hmrc.agentsubscriptionfrontend.util.toFuture
import uk.gov.hmrc.agentsubscriptionfrontend.views.html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

@Singleton
class AMLSController @Inject()(
  override val messagesApi: MessagesApi,
  override val authConnector: AuthConnector,
  val agentAssuranceConnector: AgentAssuranceConnector,
  implicit override val appConfig: AppConfig,
  override val continueUrlActions: ContinueUrlActions,
  override val metrics: Metrics,
  override val sessionStoreService: SessionStoreService)
    extends FrontendController with I18nSupport with AuthActions with SessionDataSupport with Monitoring {

  import AMLSForms._

  private val amlsBodies: Map[String, String] = AMLSLoader.load("/amls.csv")

  val showMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withManuallyAssuredAgent {
        for {
          cachedAmlsDetails <- sessionStoreService.fetchAMLSDetails
          cachedGoBackUrl   <- sessionStoreService.fetchGoBackUrl
        } yield {
          (cachedAmlsDetails, cachedGoBackUrl) match {
            case (Some(amlsDetails), mayBeGoBackUrl) =>
              val form: Map[String, String] =
                Map(
                  "amlsCode"         -> amlsBodies.find(_._2 == amlsDetails.supervisoryBody).map(_._1).getOrElse(""),
                  "membershipNumber" -> amlsDetails.membershipNumber,
                  "expiry.day"       -> amlsDetails.membershipExpiresOn.getDayOfMonth.toString,
                  "expiry.month"     -> amlsDetails.membershipExpiresOn.getMonthValue.toString,
                  "expiry.year"      -> amlsDetails.membershipExpiresOn.getYear.toString
                )
              Ok(html.money_laundering_compliance(amlsForm(amlsBodies.keySet).bind(form), amlsBodies, mayBeGoBackUrl))

            case (None, _) => Ok(html.money_laundering_compliance(amlsForm(amlsBodies.keySet), amlsBodies))
          }
        }
      }
    }
  }

  def submitMoneyLaunderingComplianceForm: Action[AnyContent] = Action.async { implicit request =>
    withSubscribingAgent { _ =>
      withManuallyAssuredAgent {
        amlsForm(amlsBodies.keys.toSet)
          .bindFromRequest()
          .fold(
            formWithErrors => toFuture(Ok(html.money_laundering_compliance(formWithErrors, amlsBodies))),
            validForm => {
              val amlsDetails = AMLSDetails(
                amlsBodies.getOrElse(validForm.amlsCode, throw new Exception("Invalid AMLS code")),
                validForm.membershipNumber,
                validForm.expiry
              )

              sessionStoreService
                .cacheAMLSDetails(amlsDetails)
                .map { _ =>
                  mark("Count-Subscription-CleanCreds-Start")
                  Redirect(routes.SubscriptionController.showCheckAnswers())
                }
            }
          )
      }
    }
  }

  private def withManuallyAssuredAgent(body: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    withInitialDetails { details =>
      agentAssuranceConnector.isManuallyAssuredAgent(details.utr).flatMap { response =>
        if (response) {
          mark("Count-Subscription-CleanCreds-Start")
          toFuture(Redirect(routes.SubscriptionController.showCheckAnswers()))
        } else body
      }
    }

}
