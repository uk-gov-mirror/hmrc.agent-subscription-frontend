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
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AuthStub, DesStubs}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._
import uk.gov.hmrc.agentsubscriptionfrontend.support.WireMockSupport
import uk.gov.hmrc.play.test.UnitSpec

class SubscriptionControllerISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {

  private val validUTR = "0123456789"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.des.port" -> wireMockPort
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

  //TODO : input data as constants
  "submitKnownFacts" should {

    "redirect to the company-auth-frontend sign-in page if the current user is not logged in" in {
      AuthStub.userIsNotAuthenticated()
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
      val result = await(controller.submitKnownFacts(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("gg/sign-in")
    }

    "redirect to the non-Agent next steps page if the current user is logged in and does not have affinity group = Agent" in {
      val sessionKeys = AuthStub.userIsAuthenticated(individual)

      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts").withSession(sessionKeys: _*)
      val result = await(controller.submitKnownFacts(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).head should include("non-agent-next-steps")
    }

    "return to check agency page for a user who supplies an invalid UTR" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> "0123456")
        .withSession(sessionKeys: _*)
      val result = await(controller.submitKnownFacts(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Check Agency Status")
    }

    "redirect to no-agency-found page for an unknown UTR" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      DesStubs.utrDoesNotExist()
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> "0000000000", "postCode" -> "BN11 3JB")
        .withSession(sessionKeys: _*)
      val result = await(controller.submitKnownFacts(request))

      status(result) shouldBe OK
      bodyOf(result) should include("No Agency Found")
    }

    "redirect to confirm agency page for a user who supplies a known UTR but an unknown post code" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      DesStubs.utrIsValid()
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
                            .withFormUrlEncodedBody("utr" -> validUTR, "postCode" -> "XXXXXX")
                            .withSession(sessionKeys: _*)
      val result = await(controller.submitKnownFacts(request))

      status(result) shouldBe OK
      bodyOf(result) should include("No Agency Found")
    }

    "redirect to confirm agency page for a user who supplies a known UTR and a known post code" in {
      val sessionKeys = AuthStub.userIsAuthenticated(subscribingAgent)
      DesStubs.utrIsValid()
      val request = FakeRequest("POST", "/agent-subscription/submit-known-facts")
        .withFormUrlEncodedBody("utr" -> validUTR, "postCode" -> "BN11 3JB")
        .withSession(sessionKeys: _*)
      val result = await(controller.submitKnownFacts(request))

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
