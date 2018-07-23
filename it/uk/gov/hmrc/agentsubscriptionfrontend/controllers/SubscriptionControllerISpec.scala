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
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.play.binders.ContinueUrl

class SubscriptionControllerISpec extends BaseISpec with SessionDataMissingSpec {
  private val utr = Utr("2000000000")
  private val knownFactsPostcode = "AA1 2AA"
  private val myAgencyKnownFactsResult =
    KnownFactsResult(
      utr = utr,
      postcode = knownFactsPostcode,
      taxpayerName = "My Business",
      isSubscribedToAgentServices = false)
  private val initialDetails =
    InitialDetails(utr, knownFactsPostcode, "My Agency", "agency@example.com", "0123 456 7890")

  private lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  private lazy val redirectUrl = "https://www.gov.uk/"

  "showInitialDetails" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showInitialDetails(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showInitialDetails(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/has-other-enrolments")
      noMetricExpectedAtThisPoint()
    }

    "show subscription details page if user has not already subscribed and has clean creds" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showInitialDetails(request))
      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      metricShouldExistsAndBeenUpdated("Count-Subscription-CleanCreds-Success")
    }

    "populate form with utr and postcode" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showInitialDetails(request))

      checkHtmlResultWithBodyText(result, s"""value="${utr.value}"""", s"""value="$knownFactsPostcode"""")
    }

    "redirect to the Check Business Type page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showInitialDetails(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showSubscriptionComplete" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showSubscriptionComplete(request))
    behave like aPageWithFeedbackLinks(
      request => {
        controller.showSubscriptionComplete(request)
      },
      authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")
    )

    "display the agency name and ARN" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

      val result =
        await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "You must save this number for your agency's records.")
      checkHtmlResultWithBodyText(result, "AARN-000-0001")
    }

    "redirect to session missing page if there is nothing in the flash scope" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

      val result = await(controller.showSubscriptionComplete(request))

      resultShouldBeSessionDataMissing(result)
    }

    "tolerate a possible short delay in the new enrolment becoming visible in auth" when {
      "there was a delay and the new enrolment is not yet visible in auth" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

        val result =
          await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionComplete.title"))
      }
      "there was no delay and the new enrolment is visible in auth" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

        val result =
          await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionComplete.title"))
      }
    }

    "contain a button to continue journey" when {
      "a continue URL exists in the session, show a generic 'Continue' button using that URL" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

        val continueUrl = ContinueUrl("/test-continue-url")

        sessionStoreService.currentSession(hc(request)).continueUrl = Some(continueUrl)
        val result =
          await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

        checkHtmlResultWithBodyText(
          result,
          s">${htmlEscapedMessage("subscriptionComplete.button.continueJourney")}</a>",
          continueUrl.url)
      }

      "no continue URL exists in the session, show a button with a link in AS services" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

        val result =
          await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

        checkHtmlResultWithBodyText(
          result,
          s">${htmlEscapedMessage("subscriptionComplete.button.continueToASAccount")}</a>",
          redirectUrl)
      }
    }

    "remove existing session" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)

      val result =
        await(controller.showSubscriptionComplete(request.withFlash("arn" -> "AARN0000001", "agencyName" -> "My Agency")))

      status(result) shouldBe 200
      sessionStoreService.allSessionsRemoved shouldBe true
    }
  }

  "submitInitialDetails" should {
    "redisplay form" when {
      "name contains invalid characters" in {
        implicit val request = subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(
          result,
          htmlEscapedMessage("subscriptionDetails.title"),
          htmlEscapedMessage("error.agency-name.invalid"))
      }

      "email is omitted" in {
        implicit val request = subscriptionDetailsRequest("email")
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email has no text in the domain part" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email does not contain an '@'" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
      }

      "email has no text in the local part" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "@domain"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(
          result,
          htmlEscapedMessage("subscriptionDetails.title"),
          "You must enter an email address")
      }

      "telephone is invalid with numbers and words" in {
        implicit val request = subscriptionDetailsRequest("telephone", Seq("telephone" -> "02073457443fff"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(
          result,
          htmlEscapedMessage("subscriptionDetails.title"),
          "You must enter a valid telephone number, for example 01234567890")
      }
    }

    "redisplay form and log a warning - hidden fields should never be invalid because they were validated when originally entered" when {
      "known facts postcode is not valid" in {
        pending
        implicit val request = subscriptionDetailsRequest("knownFactsPostcode", Seq("knownFactsPostcode" -> "1AA AA1"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
        // add check for Logger(getClass).warn here
      }

      "utr is not valid" in {
        pending
        implicit val request = subscriptionDetailsRequest("utr", Seq("utr" -> "012345"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("subscriptionDetails.title"))
        // add check for Logger(getClass).warn here
      }
    }

    "redirect back to check-business-type" when {
      "subscription form has errors and current session is missing" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))

        val result = await(controller.submitInitialDetails(request))
        resultShouldBeSessionDataMissing(result)
        noMetricExpectedAtThisPoint()
      }
    }
  }

  "returnFromAddressLookup" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitInitialDetails(request))

    "send subscription request and redirect to subscription complete" when {
      "all fields are supplied" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result = await(controller.submitInitialDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest())
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.allSessionsRemoved shouldBe false
        flash(result2).get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())
        metricShouldExistsAndBeenUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-Complete")

      }

      "all fields are supplied but address contains more than 4 lines" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result = await(controller.submitInitialDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest(), Seq("Line 4", "Line 5"))
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        sessionStoreService.allSessionsRemoved shouldBe false
        flash(result2).get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())
        // add check for Logger(getClass).warn here
        metricShouldExistsAndBeenUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-Complete")
      }

      "town is omitted" in {
        val request = subscriptionRequest(town = None)
        AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result = await(controller.submitInitialDetails(subscriptionDetailsRequest()))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", request)
        val result2 =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(request)
      }
    }

    "always send countryCode=GB to the back end as we do not currently allow non-UK addresses" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(countryCode = "GB"))

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      val detailsRequest = subscriptionDetailsRequest()
      val result = await(controller.submitInitialDetails(detailsRequest))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe "/api/dummy/callback"

      val addressId = "addr1"
      stubAddressLookupReturnedAddress(addressId, subscriptionRequest(countryCode = "AR"))
      val result2 =
        await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      status(result2) shouldBe 303
      redirectLocation(result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      verifySubscriptionRequestSent(subscriptionRequest(countryCode = "GB"))
    }

    "not mix up data from concurrent users" in {
      val request = subscriptionRequest()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      val user1Result1 = await(controller.submitInitialDetails(subscriptionDetailsRequest()))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      val request2 = subscriptionRequest2()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request2, "ARN00002")

      val user2Result1 = await(controller.submitInitialDetails(subscriptionDetailsRequest2()))
      status(user2Result1) shouldBe 303
      redirectLocation(user2Result1).head shouldBe "/api/dummy/callback"

      stubAddressLookupReturnedAddress("addr1", request)
      val user1Result2 =
        await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      verifySubscriptionRequestSent(request)

      sessionStoreService.allSessionsRemoved shouldBe false
      flash(user1Result2).get("arn") shouldBe Some("ARN00001")

      stubAddressLookupReturnedAddress("addr2", request2)
      val user2Result2 = await(
        controller.returnFromAddressLookup("addr2")(authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments)))
      verifySubscriptionRequestSent(request)

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      sessionStoreService.allSessionsRemoved shouldBe false
      flash(user2Result2).get("arn") shouldBe Some("ARN00002")
    }

    "redirect to subscription failed" when {
      "subscription request fails" in {
        AgentSubscriptionStub.subscriptionWillBeForbidden(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result0 = await(controller.submitInitialDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest())

        an[HttpException] should be thrownBy await(
          controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        sessionStoreService.allSessionsRemoved shouldBe false
        metricShouldExistsAndBeenUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-Failed",
          "Http4xxErrorCount-ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST"
        )
      }
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in {
        AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        val result0 = await(controller.submitInitialDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest())
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.CheckAgencyController.showAlreadySubscribed().url
        sessionStoreService.allSessionsRemoved shouldBe false
        metricShouldExistsAndBeenUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-AlreadySubscribed-APIResponse",
          "Http4xxErrorCount-ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST"
        )
      }
    }

    "display address_form_with_errors and report related errors" when {
      "postcode is blacklisted" in {
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.submitInitialDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10 1ZT")
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.postcode.blacklisted"))
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("invalidAddress.title"))
      }

      "the address is not valid according to DES's rules" in {
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.submitInitialDetails(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        val tooLongLine = "123456789012345678901234567890123456"
        givenAddressLookupReturnsAddress("addr1", addressLine1 = tooLongLine)
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.maxLength", 35))
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("invalidAddress.title"))
      }
    }

    "redirect to the Check Business Type page if there is no initial details in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      sessionStoreService.currentSession.initialDetails = None

      givenAddressLookupReturnsAddress("addr1")
      val result = await(controller.returnFromAddressLookup("addr1")(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitModifiedAddress" should {
    "display submit-modified-address and submit revised form if there are no errors" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

      implicit val request = desAddressForm()
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitModifiedAddress()(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
    }

    "redirect to check-business-type if there is no valid session" in {
      implicit val request = desAddressForm()
      sessionStoreService.currentSession.initialDetails = None

      val result = await(controller.submitModifiedAddress()(request))

      resultShouldBeSessionDataMissing(result)
      noMetricExpectedAtThisPoint()
    }

    "redisplay address_form_with_errors and show errors" when {
      "the address is not valid according to DES's rules" in {
        val tooLongAddressLine = "12345678901234567890123456789012345678901234567890"
        implicit val request = desAddressForm(addressLine1 = tooLongAddressLine)
        val result = await(controller.submitModifiedAddress()(request))
        status(result) shouldBe 200
        checkHtmlResultWithBodyText(result, htmlEscapedMessage("error.maxLength", 35), tooLongAddressLine)
      }
    }
  }

  private def desAddressForm(
    addressLine1: String = "1 Some Street",
    postcode: String = "AA1 1AA",
    keyToRemove: String = "",
    additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
      Seq(
        "addressLine1" -> addressLine1,
        "addressLine2" -> "Sometown",
        "addressLine3" -> "County",
        "addressLine4" -> "Address Line 4",
        "postcode"     -> postcode,
        "countryCode"  -> "GB"
      ).filter(_._1 != keyToRemove) ++ additionalParameters: _*)

  private def subscriptionDetailsRequest(
    keyToRemove: String = "",
    additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
      Seq(
        "utr"                -> utr.value,
        "knownFactsPostcode" -> knownFactsPostcode,
        "name"               -> "My Agency",
        "email"              -> "agency@example.com",
        "telephone"          -> "0123 456 7890")
        .filter(_._1 != keyToRemove) ++ additionalParameters: _*)

  private def subscriptionRequest(
    town: Option[String] = Some("Sometown"),
    county: Option[String] = Some("County"),
    postcode: String = "AA1 1AA",
    countryCode: String = "GB") =
    SubscriptionRequest(
      utr = utr,
      knownFacts = SubscriptionRequestKnownFacts(knownFactsPostcode),
      agency = Agency(
        name = "My Agency",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = town,
          addressLine3 = county,
          addressLine4 = Some("Address Line 4"),
          postcode = postcode,
          countryCode = countryCode),
        telephone = "0123 456 7890",
        email = "agency@example.com"
      )
    )

  private def subscriptionDetailsRequest2(
    keyToRemove: String = "",
    additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
      Seq(
        "utr"                -> utr.value,
        "knownFactsPostcode" -> "BA1 2AA",
        "name"               -> "My Agency 2",
        "email"              -> "agency2@example.com",
        "telephone"          -> "0123 456 7899")
        .filter(_._1 != keyToRemove) ++ additionalParameters: _*)

  private def subscriptionRequest2(town: String = "Sometown", county: String = "County", postcode: String = "AA1 1AA") =
    SubscriptionRequest(
      utr = utr,
      knownFacts = SubscriptionRequestKnownFacts("BA1 2AA"),
      agency = Agency(
        name = "My Agency 2",
        address = DesAddress(
          addressLine1 = "1 Some Street",
          addressLine2 = Some(town),
          addressLine3 = Some(county),
          addressLine4 = Some("Address Line 4"),
          postcode = postcode,
          countryCode = "GB"),
        email = "agency2@example.com",
        telephone = "0123 456 7899"
      )
    )

  private def stubAddressLookupReturnedAddress(
    addressId: String,
    subscriptionRequest: SubscriptionRequest,
    unsupportedAddressLines: Seq[String] = Seq.empty) =
    givenAddressLookupReturnsAddress(
      addressId,
      subscriptionRequest.agency.address.addressLine1,
      subscriptionRequest.agency.address.addressLine2.getOrElse(""),
      subscriptionRequest.agency.address.addressLine3.getOrElse(""),
      subscriptionRequest.agency.address.addressLine4.getOrElse(""),
      subscriptionRequest.agency.address.postcode,
      subscriptionRequest.agency.address.countryCode,
      unsupportedAddressLines
    )

  private def verifySubscriptionRequestSent(subscriptionRequest: SubscriptionRequest) =
    verify(
      postRequestedFor(urlEqualTo("/agent-subscription/subscription"))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine1))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine2.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine3.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine4.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.postcode))
        .withRequestBody(containing(subscriptionRequest.agency.address.countryCode)))
}
