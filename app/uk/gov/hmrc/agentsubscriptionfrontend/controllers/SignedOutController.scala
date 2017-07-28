/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.mvc.{Action, Results}
import uk.gov.hmrc.agentsubscriptionfrontend.repository.KnownFactsResultMongoRepository
import uk.gov.hmrc.agentsubscriptionfrontend.service.SessionStoreService
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class SignedOutController @Inject()(@Named("surveyRedirectUrl") surveyUrl: String,
                                    @Named("sosRedirectUrl") sosUrl: String,
                                    knownFactsResultMongoRepository: KnownFactsResultMongoRepository,
                                    sessionStoreService: SessionStoreService)
  extends FrontendController {

  def redirectToSos = Action.async { implicit request =>

    def returnAfterGGCredsCreatedUrl(query: String) =
      URLEncoder.encode(s"${routes.StartController.returnAfterGGCredsCreated().absoluteURL()}$query", "UTF-8")

    for {
      knownFactOpt <- sessionStoreService.fetchKnownFactsResult
      id <- knownFactOpt match {
        case Some(x) => knownFactsResultMongoRepository.create(x).map(Option.apply)
        case None => Future.successful(None)
      }
    } yield {
      val continueUrl = returnAfterGGCredsCreatedUrl(id.map(i => s"?id=$i").getOrElse(""))
      Results.SeeOther(s"$sosUrl&continue=$continueUrl").withNewSession
    }
  }

  def startSurvey = Action { implicit request =>
    Results.SeeOther(surveyUrl).withNewSession
  }
}
