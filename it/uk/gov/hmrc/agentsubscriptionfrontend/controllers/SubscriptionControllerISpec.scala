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

import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class SubscriptionControllerISpec extends UnitSpec with OneAppPerSuite {
  private implicit val materializer = app.materializer

  "showCheckAgencyStatus" should {
    "be available at /agent-subscription/check-agency-status" in {
      val result = get("/agent-subscription/check-agency-status")

      status(result) shouldBe OK
      bodyOf(result) should include("Check agency status")
    }

    "return HTML" in {
      val result = get("/agent-subscription/check-agency-status")

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

  "showSubscriptionDetails" should {
    "be available at /agent-subscription/subscription-details" in {
      val result = get("/agent-subscription/subscription-details")

      status(result) shouldBe OK
      bodyOf(result) should include("Subscription Details")
    }

    "return HTML" in {
      val result = get("/agent-subscription/subscription-details")

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

  private def get(path: String): Result = await(route(app, FakeRequest("GET", path)).get)
}
