/*
 * Copyright 2020 HM Revenue & Customs
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
  val subTasks: List[SubTask]
  val isComplete: Boolean = subTasks.forall(_.isComplete)
}

final case class AmlsTask(subTasks: List[SubTask]) extends Task {
  override val taskKey: String = "amlsTask"
}

final case class ContactDetailsTask(subTasks: List[SubTask]) extends Task {
  override val taskKey: String = "contactDetailsTask"
}

final case class MappingTask(subTasks: List[SubTask]) extends Task {
  override val taskKey: String = "mappingTask"
}

final case class CreateIDTask(subTasks: List[SubTask]) extends Task {
  override val taskKey: String = "createIDTask"
}

final case class CheckAnswersTask(subTasks: List[SubTask]) extends Task {
  override val taskKey: String = "checkAnswersTask"
}

sealed trait SubTask {
  val taskKey: String
  val showLink: Boolean
  val isComplete: Boolean
  val link: String
}

final case class AmlsSubTask(isMaa: Boolean, amlsData: Option[AmlsData]) extends SubTask {
  override val taskKey: String = "amlsSubTask"
  override val showLink: Boolean = !isMaa
  override val isComplete: Boolean = isMaa ||
    amlsData.fold(false) {
      case AmlsData(true, _, Some(_))           => true // registered (with details)
      case AmlsData(false, Some(true), Some(_)) => true // not registered, but applied for (with details)
      case _                                    => false
    }
  override val link: String = routes.AMLSController.showAmlsRegisteredPage().url
}

final case class ContactDetailsEmailSubTask(contactEmailData: Option[ContactEmailData], showLink: Boolean)
    extends SubTask {
  override val taskKey: String = "contactDetailsEmailSubTask"
  override val isComplete: Boolean = contactEmailData.flatMap(_.contactEmail).isDefined
  override val link: String = routes.ContactDetailsController.showContactEmailCheck().url
}

final case class ContactTradingNameSubTask(contactDetailsBusinessNameData: Option[String], showLink: Boolean)
    extends SubTask {
  override val taskKey: String = "contactDetailsTradingNameSubTask"
  override val isComplete: Boolean = contactDetailsBusinessNameData.isDefined
  override val link: String = ""
}

final case class ContactTradingAddressSubTask(
  contactDetailsTradingAddressData: Option[BusinessAddress],
  showLink: Boolean)
    extends SubTask {
  override val taskKey: String = "contactDetailsTradingAddressSubTask"
  override val isComplete: Boolean = contactDetailsTradingAddressData.isDefined
  override val link: String = ""
}

final case class MappingSubTask(
  cleanCredsAuthProviderId: Option[AuthProviderId],
  mappingComplete: Boolean,
  continueId: String,
  showLink: Boolean,
  appConfig: AppConfig)
    extends SubTask {
  override val taskKey: String = "mappingSubTask"
  override val isComplete: Boolean = mappingComplete
  override val link: String = appConfig.agentMappingFrontendStartUrl(continueId)
}

final case class CreateIDSubTask(cleanCredsAuthProviderId: Option[AuthProviderId], showLink: Boolean) extends SubTask {
  override val taskKey: String = "createIDSubTask"
  override val isComplete: Boolean = cleanCredsAuthProviderId.isDefined && showLink
  override val link: String = routes.BusinessIdentificationController.showCreateNewAccount().url
}

final case class CheckAnswersSubTask(showLink: Boolean) extends SubTask {
  override val taskKey: String = "checkAnswersSubTask"
  override val isComplete: Boolean = false
  override val link: String = routes.SubscriptionController.showCheckAnswers().url
}
