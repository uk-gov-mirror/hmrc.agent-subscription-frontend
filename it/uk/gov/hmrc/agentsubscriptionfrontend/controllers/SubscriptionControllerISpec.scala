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
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.AuthMocking.sessionKeysForMockAuth
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers.subscribingAgent
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.test.UnitSpec

class SubscriptionControllerISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort
    )
    .build()

  private implicit val materializer = app.materializer

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  "showCheckAgencyStatus" should {
    "redirect to the company-auth-frontend page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()
      val request = FakeRequest("GET", "/agent-subscription/check-agency-status")
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
    }

    "display the check agency status page if the current user is logged in" in {
      AuthStub.userIsAuthenticated(subscribingAgent)
      val sessionKeys = sessionKeysForMockAuth(subscribingAgent)

      val request = FakeRequest("GET", "/agent-subscription/check-agency-status").withSession(sessionKeys: _*)
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Check agency status")
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
