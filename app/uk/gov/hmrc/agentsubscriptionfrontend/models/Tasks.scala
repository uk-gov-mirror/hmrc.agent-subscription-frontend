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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData

sealed trait Task {
  val taskKey: String
  val showLink: Boolean
  val isComplete: Boolean
  val link: String
}

final case class AmlsTask(isMaa: Boolean, amlsData: Option[AmlsData]) extends Task {
  override val taskKey: String = "amlsTask"
  override val showLink: Boolean = !isMaa
  override val isComplete: Boolean = isMaa ||
    amlsData.fold(false) {
      case AmlsData(true, _, Some(_))           => true // registered (with details)
      case AmlsData(false, Some(true), Some(_)) => true // not registered, but applied for (with details)
      case _                                    => false
    }
  override val link: String = routes.AMLSController.showAmlsRegisteredPage().url
}

final case class MappingTask(
  cleanCredsAuthProviderId: Option[AuthProviderId],
  mappingComplete: Boolean,
  continueId: String,
  previousTask: Task,
  appConfig: AppConfig)
    extends Task {
  override val taskKey: String = "mappingTask"
  override val showLink: Boolean = previousTask.isComplete
  override val isComplete: Boolean = mappingComplete && previousTask.isComplete
  override val link: String = appConfig.agentMappingFrontendStartUrl(continueId)
}

final case class CreateIDTask(cleanCredsAuthProviderId: Option[AuthProviderId], previousTask: Task) extends Task {
  override val taskKey: String = "createIDTask"
  override val showLink: Boolean = previousTask.isComplete
  override val isComplete: Boolean = cleanCredsAuthProviderId.isDefined && previousTask.isComplete
  override val link: String = routes.BusinessIdentificationController.showCreateNewAccount().url
}

final case class CheckAnswersTask(previousTask: Task) extends Task {
  override val taskKey: String = "checkAnswersTask"
  override val showLink: Boolean = previousTask.isComplete
  override val isComplete: Boolean = false
  override val link: String = routes.SubscriptionController.showCheckAnswers().url
}
