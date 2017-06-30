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
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Address, Agency, KnownFactsResult, SubscriptionRequest, KnownFacts => ModelKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AddressLookupFrontendStubs, AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class SubscriptionControllerISpec extends BaseControllerISpec with SessionDataMissingSpec with AddressLookupFrontendStubs {
  private val utr = Utr("2000000000")
  private val myAgencyKnownFactsResult = KnownFactsResult(utr =
    Utr("utr"), postcode = "AA1 1AA", taxpayerName = "My Business", isSubscribedToAgentServices = false)
  private val invalidAddress = "Invalid road %@"

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]


  "showSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionDetails(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in {

      sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)
      AuthStub.isEnrolledForNonMtdServices(subscribingAgent)

      val result = await(controller.showSubscriptionDetails(authenticatedRequest()))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/has-other-enrolments")
    }

    "show description details page of user has not enrolled and has clean creds" in {

      sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)
      AuthStub.hasNoEnrolments(subscribingAgent)

      val request = authenticatedRequest()

      val result = await(controller.showSubscriptionDetails(request))
      bodyOf(result) should include(routes.SubscriptionController.showSubscriptionDetails().url)
    }

    "populate form with utr and postcode" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showSubscriptionDetails(authenticatedRequest()))

      checkHtmlResultWithBodyText(result,
        "value=\"utr\"",
        "value=\"AA1 1AA\"")
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
    behave like aPageWithFeedbackLinks(request => {
      AuthStub.hasNoEnrolments(subscribingAgent)
      controller.showSubscriptionComplete(request)
    }, authenticatedRequest().withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency"))

    "display the agency name and ARN" in {
      val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionComplete(request.withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result,
        "You must save this number for your agency's records.")
    }

    "redirect to session missing page if there is nothing in the flash scope" in {
      val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionComplete(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showCheckAgencyStatus().url)
    }

    "contain a link to the survey" in {
      val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)


      val result = await(controller.showSubscriptionComplete(request.withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "href=\"/agent-subscription/start-survey\"")

    }
  }

  "submitSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.getAddressDetails(request))

    "redirect to subscription complete" when {
      "all fields are supplied" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1")
        val result2 = await(controller.submit("addr1")(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.removeCalled shouldBe true
        flash(result2).get("agencyName") shouldBe Some("My Agency")
        flash(result2).get("arn") shouldBe Some("ARN00001")
      }

      "county is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest(county = ""))

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", county = "")
        val result2 = await(controller.submit("addr1")(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.removeCalled shouldBe true
        flash(result2).get("agencyName") shouldBe Some("My Agency")
        flash(result2).get("arn") shouldBe Some("ARN00001")
      }

      "town is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionSuccess(utr, subscriptionRequest(town = ""))

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", town = "")
        val result2 = await(controller.submit("addr1")(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }
    }

    "redirect to subscription failed" when {
      "subscription request fails" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionForbidden(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionFailed().url
        sessionStoreService.removeCalled shouldBe true
      }
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionConflict(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.CheckAgencyController.showAlreadySubscribed().url
        sessionStoreService.removeCalled shouldBe true
      }
    }

    "redisplay form" when {
      "name contains invalid characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address",
          "This field is limited to alphanumeric characters (A-Z, a-z, 0-9) and the following characters -,./")
      }

      "email is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("email")))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address")
      }

      "email has no text in the domain part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("email", Seq("email" -> "local@"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address")
      }

      "email does not contain an '@'" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("email", Seq("email" -> "local"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address")
      }

      "email has no text in the local part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("email", Seq("email" -> "@domain"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address", "Enter a valid email address.")
      }

      "telephone is invalid with numbers and words" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("telephone", Seq("telephone" -> "02073457443fff"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address", "Please enter a valid telephone number")
      }

      "postcode is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10 1ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "This postcode is blocked and cannot be used")
      }

      "postcode with whitespaces is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10     1ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "This postcode is blocked and cannot be used")
      }

      "postcode with lowercase characters is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "Ab10 1zT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "This postcode is blocked and cannot be used")
      }

      "postcode without whitespaces is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB101ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "This postcode is blocked and cannot be used")
      }

      "known facts postcode is not valid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("knownFactsPostcode", Seq("knownFactsPostcode" -> "1AA AA1"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address", "Please enter a valid postcode")
      }

      "utr is not valid" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        sessionStoreService.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(subscriptionDetailsRequest("utr", Seq("utr" -> "012345"))))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, "Add your agency address", "Please enter a valid UTR")
      }
    }
  }

  private def subscriptionDetailsRequest(keyToRemove: String = "", additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedRequest().withFormUrlEncodedBody(
      Seq(
        "utr" -> utr.value,
        "knownFactsPostcode" -> "AA1 2AA",
        "name" -> "My Agency",
        "email" -> "agency@example.com",
        "telephone" -> "0123 456 7890"
      )
        .filter(_._1 != keyToRemove) ++ additionalParameters: _*
    )

  private def subscriptionRequest(town: String = "Sometown", county: String = "County", postcode: String = "AA1 1AA") =
    SubscriptionRequest(utr = utr,
      knownFacts = ModelKnownFacts("AA1 2AA"),
      agency = Agency(name = "My Agency",
        address = Address(addressLine1 = "1 Some Street",
          addressLine2 = Some(town),
          addressLine3 = Some(county),
          postcode = Some(postcode),
          countryCode = "GB"),
        email = "agency@example.com",
        telephone = "0123 456 7890"))

}
