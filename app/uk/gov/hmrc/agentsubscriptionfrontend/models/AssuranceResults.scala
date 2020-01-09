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

case class AssuranceResults(
  isOnRefusalToDealWithList: Boolean,
  isManuallyAssured: Boolean,
  hasAcceptableNumberOfPayeClients: Option[Boolean],
  hasAcceptableNumberOfSAClients: Option[Boolean],
  hasAcceptableNumberOfVatDecOrgClients: Option[Boolean],
  hasAcceptableNumberOfIRCTClients: Option[Boolean])

object AssuranceResults {
  object RefuseToDealWith {
    def unapply(maybeAssuranceResults: Some[AssuranceResults]): Option[AssuranceResults] =
      maybeAssuranceResults.filter(_.isOnRefusalToDealWithList)
  }

  object ManuallyAssured {
    def unapply(maybeAssuranceResults: Some[AssuranceResults]): Option[AssuranceResults] =
      maybeAssuranceResults.filter(_.isManuallyAssured)
  }

  object CheckedInvisibleAssuranceAndPassed {
    def unapply(maybeAssuranceResults: Some[AssuranceResults]): Option[AssuranceResults] = maybeAssuranceResults match {
      case Some(AssuranceResults(false, false, Some(true), _, _, _)) => maybeAssuranceResults
      case Some(AssuranceResults(false, false, _, Some(true), _, _)) => maybeAssuranceResults
      case Some(AssuranceResults(false, false, _, _, Some(true), _)) => maybeAssuranceResults
      case Some(AssuranceResults(false, false, _, _, _, Some(true))) => maybeAssuranceResults
      case _                                                         => None
    }
  }

  object CheckedInvisibleAssuranceAndFailed {
    def unapply(maybeAssuranceResults: Some[AssuranceResults]): Option[AssuranceResults] = maybeAssuranceResults match {
      case Some(AssuranceResults(false, false, Some(false), Some(false), Some(false), Some(false))) =>
        maybeAssuranceResults
      case Some(AssuranceResults(false, false, None, Some(false), Some(false), Some(false))) => maybeAssuranceResults
      case _                                                                                 => None
    }
  }
}
