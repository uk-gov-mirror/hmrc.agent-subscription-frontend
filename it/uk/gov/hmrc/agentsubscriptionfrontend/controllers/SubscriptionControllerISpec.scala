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
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Address, Agency, KnownFactsResult, SubscriptionRequest, KnownFacts => ModelKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class SubscriptionControllerISpec extends BaseControllerISpec with SessionDataMissingSpec {
  private val utr  = "0123456789"
  private val myAgencyKnownFactsResult = KnownFactsResult(utr = "utr", postcode = "AA1 1AA", organisationName = "My Business", isSubscribedToAgentServices = false)

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  "showSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionDetails(request))

    "populate form with utr and postcode and display registration name" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showSubscriptionDetails(authenticatedRequest()))

      checkHtmlResultWithBodyText("value=\"utr\"", result)
      checkHtmlResultWithBodyText("value=\"AA1 1AA\"", result)
      checkHtmlResultWithBodyText("My Business", result)
    }

    "redirect to the Check Agency Status page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      val request = authenticatedRequest()

      val result = await(controller.showSubscriptionDetails(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showSubscriptionComplete" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionComplete(request))

    "display the agency name and ARN" in {
      val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)


      val result = await(controller.showSubscriptionComplete(request.withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText("My Agency is subscribed to agent services", result)
      checkHtmlResultWithBodyText(">ARN0001<", result)
    }

    "redirect to session missing page if there is nothing in the flash scope" in {
      val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionComplete(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showCheckAgencyStatus().url)
    }
  }

  "submitSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitSubscriptionDetails(request))

    "redirect to subscription complete" when {
      "all fields are supplied" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.removeCalled shouldBe true
        flash(result).get("agencyName") shouldBe Some("My Agency")
        flash(result).get("arn") shouldBe Some("ARN00001")
      }

      "county is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest.copy(
          agency = subscriptionRequest.agency.copy(
            address = subscriptionRequest.agency.address.copy(addressLine3 = None))))

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine3")))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "town is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest.copy(
          agency = subscriptionRequest.agency.copy(
            address = subscriptionRequest.agency.address.copy(addressLine2 = None))))

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine2")))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }
    }

    "redirect to subscription failed" when {
      "postcodes don't match" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionForbidden(utr, subscriptionRequest)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionFailed().url
        sessionStoreService.removeCalled shouldBe true
      }
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionConflict(utr, subscriptionRequest)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.CheckAgencyController.showAlreadySubscribed().url
        sessionStoreService.removeCalled shouldBe true
      }
    }

    "redisplay form" when {
      "name is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("name")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
        sessionStoreService.removeCalled shouldBe false
      }

      "name is longer than 40 characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("name", Seq("name" -> "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "email is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("email")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "email has no text in the local part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("email", Seq("email" -> "@domain"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "email has no text in the domain part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("email", Seq("email" -> "local@"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "email does not contain an '@'" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("email", Seq("email" -> "local"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "telephone is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("telephone")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "telephone is invalid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("telephone", Seq("telephone" -> "12345"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }


      "building and street is omitted" in {
         AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine1")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "building and street is whitespace only" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("addressLine1", Seq("addressLine1" -> "    "))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }


      "building and street is longer than 35 characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(
            subscriptionDetailsRequest("addressLine1", Seq("addressLine1" -> "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "town is longer than 35 characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(
          subscriptionDetailsRequest("addressLine2", Seq("addressLine2" -> "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "county is longer than 35 characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(
          subscriptionDetailsRequest("addressLine3", Seq("addressLine3" -> "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "postcode is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("postcode")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "postcode is not valid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("postcode", Seq("postcode" -> "1AA AA1"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "known facts postcode is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("knownFactsPostcode")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "known facts postcode is not valid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("knownFactsPostcode", Seq("knownFactsPostcode" -> "1AA AA1"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "utr is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("utr")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }

      "utr is not valid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitSubscriptionDetails(subscriptionDetailsRequest("utr", Seq("utr" -> "012345"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText("Subscribe to Agent Services", result)
        checkHtmlResultWithBodyText("My Business", result)
      }
    }
  }

  private def subscriptionDetailsRequest(keyToRemove: String = "", additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedRequest().withFormUrlEncodedBody(
        Seq("utr" -> utr,
            "knownFactsPostcode" -> "AA1 2AA",
            "name" -> "My Agency",
            "email" -> "agency@example.com",
            "telephone" -> "0123 456 7890",
            "addressLine1" -> "1 Some Street",
            "addressLine2" -> "Sometown",
            "addressLine3" -> "County",
            "postcode" -> "AA1 1AA").filter(_._1 != keyToRemove) ++ additionalParameters: _*
    )

  private val subscriptionRequest =
    SubscriptionRequest(utr = utr,
      knownFacts = ModelKnownFacts("AA1 2AA"),
      agency = Agency(name = "My Agency",
        address = Address(addressLine1 = "1 Some Street",
                          addressLine2 = Some("Sometown"),
                          addressLine3 = Some("County"),
                          postcode = "AA1 1AA",
              countryCode = "GB"),
        email = "agency@example.com",
        telephone = "0123 456 7890"))

}
