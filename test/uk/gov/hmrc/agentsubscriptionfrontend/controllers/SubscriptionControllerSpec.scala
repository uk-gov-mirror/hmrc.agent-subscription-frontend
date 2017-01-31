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

import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestAppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestMessagesApi.testMessagesApi
import uk.gov.hmrc.play.test.UnitSpec

class SubscriptionControllerSpec extends UnitSpec {

  val fakeRequest = FakeRequest("GET", "/check-agency-status")

  val controller = new SubscriptionController(testMessagesApi)(TestAppConfig)

  "showCheckAgencyStatus" should {
    "return 200" in {
      val result = controller.showCheckAgencyStatus(fakeRequest)
      status(result) shouldBe OK
    }

    "return HTML" in {
      val result = controller.showCheckAgencyStatus(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }
}
