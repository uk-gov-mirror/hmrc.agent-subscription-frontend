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

sealed trait ValidationResult extends Product with Serializable

object ValidationResult {
  case object Pass extends ValidationResult
  case class Failure(reasons: Set[FailureReason]) extends ValidationResult

  object Failure {
    def apply(reason: FailureReason): Failure = Failure(Set(reason))
  }

  sealed trait FailureReason extends Product with Serializable

  object FailureReason {
    case object InvalidEmail extends FailureReason
    case object InvalidBusinessName extends FailureReason
    case object InvalidBusinessAddress extends FailureReason
    case object DisallowedPostcode extends FailureReason
  }
}
