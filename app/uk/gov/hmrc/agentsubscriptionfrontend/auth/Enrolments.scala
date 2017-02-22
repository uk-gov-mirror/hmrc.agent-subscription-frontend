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

import play.api.libs.json.{Format, Json}

case class EnrolmentIdentifier(key: String, value: String)
case class Enrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String) {
  val isActivated: Boolean = state equalsIgnoreCase "Activated"
  def identifier(key: String): Option[String] = identifiers.find(_.key == key).map(_.value)
}

object Enrolment {
  implicit val idFormat: Format[EnrolmentIdentifier] = Json.format[EnrolmentIdentifier]
  implicit val format: Format[Enrolment] = Json.format[Enrolment]
}
