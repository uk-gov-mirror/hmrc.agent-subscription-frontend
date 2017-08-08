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

import cats.data.NonEmptyList
import org.scalatest._
import org.scalatestplus.play.OneAppPerSuite
import play.api.data.validation.ValidationError
import play.api.i18n.Messages.Implicits._
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.SubscriptionController.renderErrors

class RenderErrorsSpec extends WordSpec with Matchers with OneAppPerSuite {

  "renderErrors function" should {
    "concatenate invalid and blacklist error messages" in {
      val validationErrors = NonEmptyList.of(ValidationError("error.postcode.invalid"), ValidationError("error.postcode.blacklisted"))
      val result: String = renderErrors(validationErrors)

      result shouldEqual "You have entered an invalid postcode, You can't use the postcode you've entered"
    }

    "concatenate invalid and maxLength error messages" in {
      val addressLine = "IpwichoIpwichoIpwichoIpwicho"
      val maxLength = 40
      val validationErrors = NonEmptyList.of(ValidationError("error.postcode.invalid"),
        ValidationError("error.address.maxLength", maxLength, addressLine))
      val result: String = renderErrors(validationErrors)

      result shouldEqual s"You have entered an invalid postcode, Length of line $addressLine must be up to $maxLength"
    }

    "concatenate invalid and maxLength error messages for 2 lines" in {
      val addressLine1 = "IpwichoIpwichoIpwichoIpwicho"
      val addressLine2 = addressLine1 + "Ipwich"
      val maxLength = 40

      val validationErrors = NonEmptyList.of(ValidationError("error.postcode.invalid"),
        ValidationError("error.address.maxLength", maxLength, addressLine1),
        ValidationError("error.address.maxLength", maxLength, addressLine2))
      val result: String = renderErrors(validationErrors)

      result shouldEqual s"You have entered an invalid postcode, " +
        s"Length of line $addressLine1 must be up to $maxLength, " +
        s"Length of line $addressLine2 must be up to $maxLength"
    }
  }
}
