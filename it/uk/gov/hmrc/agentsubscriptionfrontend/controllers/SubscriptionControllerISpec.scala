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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{Agency, DesAddress, KnownFactsResult, SubscriptionRequest, KnownFacts => ModelKnownFacts}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUsers._

class SubscriptionControllerISpec extends BaseISpec with SessionDataMissingSpec {
  private val utr = Utr("2000000000")
  private val myAgencyKnownFactsResult = KnownFactsResult(utr =
    Utr("utr"), postcode = "AA1 1AA", taxpayerName = "My Business", isSubscribedToAgentServices = false)

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  private lazy val redirectUrl = "https://www.gov.uk/"

  "showSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionDetails(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in {
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      AuthStub.isEnrolledForNonMtdServices(subscribingAgent)

      val result = await(controller.showSubscriptionDetails(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/has-other-enrolments")
    }

    "show description details page of user has not enrolled and has clean creds" in {
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionDetails(request))
      bodyOf(result) should include(routes.SubscriptionController.showSubscriptionDetails().url)
    }

    "populate form with utr and postcode" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showSubscriptionDetails(request))

      checkHtmlResultWithBodyText(result,
        "value=\"utr\"",
        "value=\"AA1 1AA\"")
    }

    "redirect to the Check Agency Status page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()

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
      implicit val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionComplete(request.withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result,
        "You must save this number for your agency's records.")
    }

    "redirect to session missing page if there is nothing in the flash scope" in {
      implicit val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)

      val result = await(controller.showSubscriptionComplete(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CheckAgencyController.showCheckAgencyStatus().url)
    }

    "contain a link in AS services" in {
      implicit val request = authenticatedRequest()
      AuthStub.hasNoEnrolments(subscribingAgent)


      val result = await(controller.showSubscriptionComplete(request.withFlash("arn" -> "ARN0001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      bodyOf(result) should include(redirectUrl)
    }
  }

  "submitSubscriptionDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.getAddressDetails(request))

    "send subscription request and redirect to subscription complete" when {
      "all fields are supplied" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result = await(controller.getAddressDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest())
        val result2 = await(controller.submit(addressId)(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.allSessionsRemoved shouldBe true
        flash(result2).get("agencyName") shouldBe Some("My Agency")
        flash(result2).get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())

      }

      "all fields are supplied but address contains more than 4 lines" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result = await(controller.getAddressDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest(), Seq("Line 4", "Line 5"))
        val result2 = await(controller.submit(addressId)(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.allSessionsRemoved shouldBe true
        flash(result2).get("agencyName") shouldBe Some("My Agency")
        flash(result2).get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())
        // add check for Logger.warn here
      }

      "town is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        val request = subscriptionRequest(town = "")
        AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result = await(controller.getAddressDetails(subscriptionDetailsRequest()))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", request)
        val result2 = await(controller.submit("addr1")(authenticatedRequest()))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(request)
      }
    }

    "not mix up data from concurrent users" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      val request = subscriptionRequest()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      val user1Result1 = await(controller.getAddressDetails(subscriptionDetailsRequest()))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      AuthStub.hasNoEnrolments(subscribingAgent2)
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest2(), "ARN00002")

      val user2Result1 = await(controller.getAddressDetails(subscriptionDetailsRequest2()))
      status(user2Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      stubAddressLookupReturnedAddress("addr1", request)
      val user1Result2 = await(controller.submit("addr1")(authenticatedRequest()))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      verifySubscriptionRequestSent(request)

      sessionStoreService.allSessionsRemoved shouldBe false
      flash(user1Result2).get("agencyName") shouldBe Some("My Agency")
      flash(user1Result2).get("arn") shouldBe Some("ARN00001")

      stubAddressLookupReturnedAddress("addr2", request)
      val user2Result2 = await(controller.submit("addr2")(authenticatedRequest(user = subscribingAgent2)))
      verifySubscriptionRequestSent(request)

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      sessionStoreService.allSessionsRemoved shouldBe true
      flash(user2Result2).get("agencyName") shouldBe Some("My Agency 2")
      flash(user2Result2).get("arn") shouldBe Some("ARN00002")
    }

    "redirect to subscription failed" when {
      "subscription request fails" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest())

        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionFailed().url
        sessionStoreService.allSessionsRemoved shouldBe true
      }
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest())
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.CheckAgencyController.showAlreadySubscribed().url
        sessionStoreService.allSessionsRemoved shouldBe true
      }
    }

    "redisplay form" when {
      "name contains invalid characters" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"),
          "This field is limited to alphanumeric characters (A-Z, a-z, 0-9) and the following characters -,./")
      }

      "email is omitted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("email")
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email has no text in the domain part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email does not contain an '@'" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email has no text in the local part" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "@domain"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"), "Enter a valid email address.")
      }

      "telephone is invalid with numbers and words" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("telephone", Seq("telephone" -> "02073457443fff"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"), "Please enter a valid telephone number")
      }

      "postcode is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10 1ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.postcode.blacklisted"))
      }

      "postcode with whitespaces is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10     1ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.postcode.blacklisted"))
      }

      "postcode with lowercase characters is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "Ab10 1zT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.postcode.blacklisted"))
      }

      "postcode without whitespaces is blacklisted" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.getAddressDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB101ZT")
        val result = await(controller.submit("addr1")(authenticatedRequest()))


        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.postcode.blacklisted"))
      }
    }

    "redisplay form and log a warning - hidden fields should never be invalid because they were validated when originally entered" when {
      "known facts postcode is not valid" in {
        pending
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("knownFactsPostcode", Seq("knownFactsPostcode" -> "1AA AA1"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
        // add check for Logger.warn here
      }

      "utr is not valid" in {
        pending
        AuthStub.hasNoEnrolments(subscribingAgent)
        implicit val request = subscriptionDetailsRequest("utr", Seq("utr" -> "012345"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.getAddressDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
        // add check for Logger.warn here
      }
    }

    "redirect back to check-agency-status" when {
      "subscription form has errors and current session is missing" in {
        AuthStub.hasNoEnrolments(subscribingAgent)
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))

        val result = await(controller.getAddressDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.CheckAgencyController.showCheckAgencyStatus().url
      }
    }
  }

  "submit" should {
    "redirect to the Check Agency Status page if there is no initial details in session because the user has returned to a bookmark" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      sessionStoreService.currentSession.initialDetails = None

      givenAddressLookupReturnsAddress("addr1")
      val result = await(controller.submit("addr1")(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "beginJourney" should {
    "redirect to address lookup journey" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
      implicit val request = authenticatedRequest()

      val result = await(controller.beginJourney("agents-subscr")(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe "/api/dummy/callback"
    }
  }

  "showSubscriptionFailed" should {
    "show subscription failed page" in {
      AuthStub.hasNoEnrolments(subscribingAgent)
      implicit val request = authenticatedRequest()
      val result = await(controller.showSubscriptionFailed(request))
      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "Postcodes do not match")
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
      agency =
        Agency(
          name = "My Agency",
          address = DesAddress(
              addressLine1 = "1 Some Street",
              addressLine2 = Some(town),
              addressLine3 = Some(county),
              addressLine4 = Some("Address Line 4"),
              postcode = Some(postcode),
              countryCode = "GB"),
      telephone = "0123 456 7890",
      email = "agency@example.com"))

  private def subscriptionDetailsRequest2(keyToRemove: String = "", additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedRequest(user = subscribingAgent2).withFormUrlEncodedBody(
      Seq(
        "utr" -> utr.value,
        "knownFactsPostcode" -> "BA1 2AA",
        "name" -> "My Agency 2",
        "email" -> "agency2@example.com",
        "telephone" -> "0123 456 7899"
      )
        .filter(_._1 != keyToRemove) ++ additionalParameters: _*
    )

  private def subscriptionRequest2(town: String = "Sometown", county: String = "County", postcode: String = "AA1 1AA") =
    SubscriptionRequest(utr = utr,
      knownFacts = ModelKnownFacts("BA1 2AA"),
      agency = Agency(name = "My Agency 2",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = Some(town),
          addressLine3 = Some(county),
          addressLine4 = Some("Address Line 4"),
          postcode = Some(postcode),
          countryCode = "GB"),
        email = "agency2@example.com",
        telephone = "0123 456 7899"))

  private def stubAddressLookupReturnedAddress(addressId: String,
                                               subscriptionRequest: SubscriptionRequest,
                                               unsupportedAddressLines: Seq[String] = Seq.empty) = {
    givenAddressLookupReturnsAddress(
      addressId,
      subscriptionRequest.agency.address.addressLine1,
      subscriptionRequest.agency.address.addressLine2.getOrElse(""),
      subscriptionRequest.agency.address.addressLine3.getOrElse(""),
      subscriptionRequest.agency.address.addressLine4.getOrElse(""),
      subscriptionRequest.agency.address.postcode.getOrElse(""),
      subscriptionRequest.agency.address.countryCode,
      unsupportedAddressLines
    )
  }

  private def verifySubscriptionRequestSent(subscriptionRequest: SubscriptionRequest) = {
    verify(postRequestedFor(urlEqualTo("/agent-subscription/subscription"))
      .withRequestBody(containing(subscriptionRequest.agency.address.addressLine1))
      .withRequestBody(containing(subscriptionRequest.agency.address.addressLine2.getOrElse("")))
      .withRequestBody(containing(subscriptionRequest.agency.address.addressLine3.getOrElse("")))
      .withRequestBody(containing(subscriptionRequest.agency.address.addressLine4.getOrElse("")))
      .withRequestBody(containing(subscriptionRequest.agency.address.postcode.getOrElse("")))
      .withRequestBody(containing(subscriptionRequest.agency.address.countryCode))
    )
  }

}
