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

import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class SubscriptionControllerISpec extends BaseControllerISpec {

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  "showSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionDetails(request))

    "be available" in {
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionDetails(authenticatedRequest))

     checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
    }
  }

  "showSubscriptionComplete" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionComplete(request))
  }

  "submitSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitSubscriptionDetails(request))

    "redirect to subscription complete" when {
      "all fields are supplied" in {
        AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "county is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine3")))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }
    }

    "redisplay form" when {
      "name is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("name")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }

      "email is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("email")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }

      "telephone is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("telephone")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }

      "building and street is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine1")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }

      "town is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine2")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }

      "postcode is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("postcode")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
      }
    }
  }

  private def subscriptionDetailsRequest(keyToRemove: String = "") =
    authenticatedRequest.withFormUrlEncodedBody(
        Seq("name" -> "My Agency",
            "email" -> "agency@example.com",
            "telephone" -> "0123 456 7890",
            "addressLine1" -> "1 Some Street",
            "addressLine2" -> "Sometown",
            "addressLine3" -> "County",
            "postcode" -> "AA1 1AA").filter(_._1 != keyToRemove): _*
    )


}
