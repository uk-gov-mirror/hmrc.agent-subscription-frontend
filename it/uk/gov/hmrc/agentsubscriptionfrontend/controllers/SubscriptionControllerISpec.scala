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
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.test.UnitSpec

class SubscriptionControllerISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  private val validUTR = "0123456789"
  private val invalidUtr = "0123456"
  private val validPostcode = "AA1 1AA"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.agent-subscription.port" -> wireMockPort
    )
    .build()

  private implicit val materializer = app.materializer

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  "showCheckAgencyStatus" should {
    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()
      val request = FakeRequest("GET", "/agent-subscription/check-agency-status")
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(individual)

      val request = FakeRequest("GET", "/agent-subscription/check-agency-status").withSession(sessionKeys: _*)
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("non-agent-next-steps")
    }

    "display the check agency status page if the current user is logged in and has affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)

      val request = FakeRequest("GET", "/agent-subscription/check-agency-status").withSession(sessionKeys: _*)
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Check Agency Status")
    }

  }

  "checkAgencyStatus" should {

    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(individual)

      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts").withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("non-agent-next-steps")
    }

    "return a 200 response to redisplay the form with an error message for invalid UTR" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Check Agency Status")
      responseBody should include ("Please enter a valid UTR")
      responseBody should include (invalidUtr)
      responseBody should include (validPostcode)
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Check Agency Status")
      responseBody should include ("This field is required")
    }

    "redirect to no-agency-found page when no matching registration found by agent-subscription" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val utr = "0000000000"
      AgentSubscriptionStub.withNonMatchingUtrAndPostcode(utr, validPostcode)
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> utr, "postcode" -> validPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      bodyOf(result) should include("No Agency Found")
    }

    "redirect to confirm agency page for a user who supplies a UTR and post code that agent-subscription finds a matching registration for" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUTR, validPostcode)
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> validUTR, "postcode" -> validPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Confirm Your Agency")
    }

  }

  "showNonAgentNextSteps" should {
    "display the non-agent next steps page if the current user is logged in" in {
      val sessionKeys = AuthStub.userIsAuthenticated(individual)

      val request = FakeRequest(routes.SubscriptionController.showNonAgentNextSteps()).withSession(sessionKeys: _*)
      val result = await(controller.showNonAgentNextSteps(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Affinity Group")
    }

    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()

      val request = FakeRequest(routes.SubscriptionController.showNonAgentNextSteps())
      val result = await(controller.showNonAgentNextSteps(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
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
