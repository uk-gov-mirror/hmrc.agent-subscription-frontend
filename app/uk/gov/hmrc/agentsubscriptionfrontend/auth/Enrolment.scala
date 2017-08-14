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

package uk.gov.hmrc.agentsubscriptionfrontend.auth

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel

case class EnrolmentIdentifier(key: String, value: String)

case class Enrolment(key: String,
                     identifiers: Seq[EnrolmentIdentifier],
                     state: String,
                     confidenceLevel: ConfidenceLevel,
                     delegatedAuthRule: Option[String] = None) {

  def getIdentifier(name: String): Option[EnrolmentIdentifier] = identifiers.find {
    _.key.equalsIgnoreCase(name)
  }

  def isActivated: Boolean = state.toLowerCase == "activated"
}

object Enrolment {
  implicit val idFormat = Json.format[EnrolmentIdentifier]
  implicit val writes = Json.writes[Enrolment].transform { json: JsValue =>
    json match {
      case JsObject(props) => JsObject(props + ("enrolment" -> props("key")) - "key")
    }
  }
  implicit val reads: Reads[Enrolment] = ((__ \ "key").read[String] and
    (__ \ "identifiers").readNullable[Seq[EnrolmentIdentifier]] and
    (__ \ "state").readNullable[String] and
    (__ \ "confidenceLevel").readNullable[ConfidenceLevel] and
    (__ \ "delegatedAuthRule").readNullable[String]) {
    (key, optIds, optState, optCL, optDelegateRule) =>
      Enrolment(
        key,
        optIds.getOrElse(Seq()),
        optState.getOrElse("Activated"),
        optCL.getOrElse(ConfidenceLevel.L0),
        optDelegateRule
      )
  }

  def apply(key: String): Enrolment = apply(key, Seq(), "Activated", ConfidenceLevel.L0, None)
}
