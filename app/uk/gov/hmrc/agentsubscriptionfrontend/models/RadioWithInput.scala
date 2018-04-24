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

package uk.gov.hmrc.agentsubscriptionfrontend.models

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

case class RadioWithInput(
  value: Option[Boolean],
  messageOfTrueRadioChoice: Option[String],
  messageOfFalseRadioChoice: Option[String])

object RadioWithInput {
  def radioChoice: Constraint[Option[Boolean]] = Constraint[Option[Boolean]] { fieldValue: Option[Boolean] =>
    if (fieldValue.isDefined)
      Valid
    else
      Invalid(ValidationError("error.confirmResponse.invalid"))
  }

  val confirmResponseForm: Form[RadioWithInput] = Form[RadioWithInput](
    mapping(
      "confirmResponse"                    -> optional(boolean).verifying(radioChoice),
      "confirmResponse-true-hidden-input"  -> optional(text),
      "confirmResponse-false-hidden-input" -> optional(text)
    )(RadioWithInput.apply)(RadioWithInput.unapply))
}
