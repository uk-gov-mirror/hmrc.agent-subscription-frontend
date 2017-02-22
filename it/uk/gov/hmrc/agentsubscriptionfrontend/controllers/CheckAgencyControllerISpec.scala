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

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class CheckAgencyControllerISpec extends BaseControllerISpec {

  private val validUtr = "0123456789"
  private val invalidUtr = "0123456"
  private val validPostcode = "AA1 1AA"
  private val invalidPostcode = "not a postcode"

  private lazy val controller: CheckAgencyController = app.injector.instanceOf[CheckAgencyController]

  "showCheckAgencyStatus" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCheckAgencyStatus(request))

    "display the check agency status page if the current user is logged in and has affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = await(controller.showCheckAgencyStatus(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Check Agency Status")
    }

  }

  "checkAgencyStatus" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.checkAgencyStatus(request))

    "return a 200 response to redisplay the form with an error message for invalidly-formatted UTR" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest()
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

    "return a 200 response to redisplay the form with an error message for invalidly-formatted postcode" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest()
        .withFormUrlEncodedBody("utr" -> validUtr, "postcode" -> invalidPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include("Check Agency Status")
      responseBody should include ("Please enter a valid postcode")
      responseBody should include (validUtr)
      responseBody should include (invalidPostcode)
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest()
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
      val request = FakeRequest()
        .withFormUrlEncodedBody("utr" -> utr, "postcode" -> validPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showNoAgencyFound().url)
    }

    "redirect to confirm agency page for a user who supplies a UTR and post code that agent-subscription finds a matching registration for" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      AgentSubscriptionStub.withMatchingUtrAndPostcode(validUtr, validPostcode)
      val request = FakeRequest()
        .withFormUrlEncodedBody("utr" -> validUtr, "postcode" -> validPostcode)
        .withSession(sessionKeys: _*)
      val result = await(controller.checkAgencyStatus(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showConfirmYourAgency().url)
    }

  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoAgencyFound(request))

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = await(controller.showNoAgencyFound(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("No Agency Found")
    }
  }

  "showConfirmYourAgency" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmYourAgency(request))

    "display the confirm your agency page if the current user is logged in and has affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)

      val request = FakeRequest().withSession(sessionKeys: _*)
      val result = await(controller.showConfirmYourAgency(request))

      status(result) shouldBe OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      bodyOf(result) should include("Confirm Your Agency")
    }
  }


  private def get(path: String): Result = await(route(app, FakeRequest("GET", path)).get)
}
