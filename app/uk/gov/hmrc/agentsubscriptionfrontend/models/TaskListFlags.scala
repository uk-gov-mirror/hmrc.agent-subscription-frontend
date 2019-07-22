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
import play.api.libs.json.{Json, OFormat}

/**
  * Simple data structure which represents the user's journey state on the task list page
  * Tasks are complete when either a) they have been done OR b) they are not required (implicitly complete)
  * Tasks must be completed in a specific order; the next task is not available until all previous steps are done
  */
case class TaskListFlags(
  amlsTaskComplete: Boolean = false,
  isMAA: Boolean = false,
  createTaskComplete: Boolean = false,
  checkAnswersComplete: Boolean = false) // no more editing allowed once done

// TODO remove when no longer needed in AgentSession
object TaskListFlags {
  implicit val formats: OFormat[TaskListFlags] = Json.format
}
