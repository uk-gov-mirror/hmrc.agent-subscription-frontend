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

import java.net.URLEncoder

import javax.inject.{Inject, Named, Singleton}
import play.api.mvc.Action
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.agentsubscriptionfrontend.support.CallOps.addParamsToUrl
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

@Singleton
class SignedOutController @Inject()(
  knownFactsResultMongoRepository: KnownFactsResultMongoRepository,
  sessionStoreService: SessionStoreService)(implicit appConfig: AppConfig)
    extends FrontendController {

  def redirectToSos = Action.async { implicit request =>
    for {
      knownFactOpt <- sessionStoreService.fetchKnownFactsResult
      id <- knownFactOpt match {
             case Some(x) => knownFactsResultMongoRepository.create(x).map(Option.apply)
             case None    => Future successful None
           }
      agentSubContinueUrl <- sessionStoreService.fetchContinueUrl
    } yield {
      val continueUrl =
        addParamsToUrl(
          "/agent-subscription/return-after-gg-creds-created",
          "id"       -> id.map(_.toString),
          "continue" -> agentSubContinueUrl.map(_.url))
      SeeOther(addParamsToUrl(appConfig.sosRedirectUrl, "continue" -> Some(continueUrl))).withNewSession
    }
  }

  def signOutWithContinueUrl = Action.async { implicit request =>
    sessionStoreService.fetchContinueUrl.map { maybeContinueUrl =>
      val signOutUrlWithContinueUrl =
        addParamsToUrl(appConfig.companyAuthSignInUrl, "continue" -> maybeContinueUrl.map(_.url))
      SeeOther(signOutUrlWithContinueUrl).withNewSession
    }
  }

  def startSurvey = Action { implicit request =>
    SeeOther(appConfig.surveyRedirectUrl).withNewSession
  }

  def redirectToASAccountPage = Action { implicit request =>
    SeeOther(appConfig.agentServicesAccountUrl).withNewSession
  }
}
