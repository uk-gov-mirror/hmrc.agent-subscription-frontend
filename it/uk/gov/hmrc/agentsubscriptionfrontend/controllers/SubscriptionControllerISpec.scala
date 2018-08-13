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
import org.jsoup.Jsoup
import play.api.mvc.{AnyContent, Request}
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, MappingStubs}
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.http.{BadRequestException, HttpException}
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
      result should containMessages("subscriptionDetails.title")
      metricShouldExistAndBeUpdated("Count-Subscription-CleanCreds-Success")
    }

    "populate form with utr and postcode" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

      val result = await(controller.showInitialDetails(request))

      result should containSubstrings(
        s"""value="${utr.value}"""",
        s"""value="$knownFactsPostcode"""")
    }

    "redirect to the /check-business-type page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showInitialDetails(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showLinkAccountType (GET /link-account)" should {
    trait RequestAndResult {
      val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
        .withSession("arn" -> "AARN0000001")
      val result = await(controller.showLinkAccount(request))
      val doc = Jsoup.parse(bodyOf(result))
    }

    behave like anAgentAffinityGroupOnlyEndpoint(controller.showLinkAccount(_))

    "contain page titles and content" in new RequestAndResult {
      result should containMessages(
        "linkAccount.title",
        "linkAccount.p1",
        "linkAccount.p2",
        "linkAccount.bullet-list.1",
        "linkAccount.bullet-list.2",
        "linkAccount.p3",
        "linkAccount.p4",
        "linkAccount.legend",
        "linkAccount.option.yes",
        "linkAccount.option.no"
      )
    }

    "contain radio options for Yes and No" in new RequestAndResult {
      // Check form's radio inputs have correct values
      doc.getElementById("autoMapping-yes").`val`() shouldBe "yes"
      doc.getElementById("autoMapping-no").`val`() shouldBe "no"
    }

    "form should POST to /link-account" in new RequestAndResult {
      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.SubscriptionController.submitLinkAccount().url
    }

    "contain a continue button to submit form" in new RequestAndResult {
      val continueBtn = doc.getElementById("continue")
      continueBtn.hasClass("button") shouldBe true
      continueBtn.attr("type") shouldBe "submit"
      continueBtn.text() shouldBe htmlEscapedMessage("button.continue")
    }

    "tolerate a possible short delay in the new enrolment becoming visible in auth" when {
      "there was a delay and the new enrolment is not yet visible in auth" in {
        val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withSession("arn" -> "AARN0000001")
        val result = await(controller.showLinkAccount(request))
        result should containMessages("linkAccount.title")
      }
      "there was no delay and the new enrolment is visible in auth" in {
        val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT).withSession("arn" -> "AARN0000001")
        val result = await(controller.showLinkAccount(request))
        result should containMessages("linkAccount.title")
      }
    }

    "redirect to /check-business-type if subscribed arn is missing from session" in {
      val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      resultShouldBeSessionDataMissing(await(controller.showLinkAccount(request)))
    }
  }

  "showLinkAccountType (POST /link-account)" when {
    class RequestWithSessionDetails(autoMappingFormValue: String) {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
        .withFormUrlEncodedBody("autoMapping" -> autoMappingFormValue)
        .withSession("arn" -> "AARN0000001")
      sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
    }

    def resultOf(request: Request[AnyContent]) = await(controller.submitLinkAccount(request))

    behave like anAgentAffinityGroupOnlyEndpoint(resultOf)

    "choice is Yes" should {
      "redirect to /subscription-complete" in new RequestWithSessionDetails(autoMappingFormValue = "yes") {
        MappingStubs.givenMappingUpdateToPostSubscription(utr)
        resultOf(request).header.headers(LOCATION) shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "keep the ARN in the session" in new RequestWithSessionDetails(autoMappingFormValue = "yes") {
        MappingStubs.givenMappingUpdateToPostSubscription(utr)
        resultOf(request).session.get("arn") shouldBe Some("AARN0000001")
      }

      "update the pre-subscription mappings with the ARN" in new RequestWithSessionDetails(autoMappingFormValue = "yes") {
        MappingStubs.givenMappingUpdateToPostSubscription(utr)
        val result = resultOf(request)
        MappingStubs.verifyMappingUpdateToPostSubscriptionCalled(utr)
      }
    }

    "choice is No" should {
      "redirect to /subscription-complete" in new RequestWithSessionDetails(autoMappingFormValue = "no") {
        MappingStubs.givenMappingDeletePreSubscription(utr)
        resultOf(request).header.headers(LOCATION) shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      }

      "keep the ARN in the session" in new RequestWithSessionDetails(autoMappingFormValue = "no") {
        MappingStubs.givenMappingDeletePreSubscription(utr)
        resultOf(request).session.get("arn") shouldBe Some("AARN0000001")
      }

      "delete the pre-subscription mappings" in new RequestWithSessionDetails(autoMappingFormValue = "no") {
        MappingStubs.givenMappingDeletePreSubscription(utr)
        val result = resultOf(request)
        MappingStubs.verifyMappingDeletePreSubscriptionCalled(utr)
      }
    }

    "choice is missing" should {
      "return 200 and redisplay the /link-account page with an error message for missing choice" in {
        val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
          .withSession("arn" -> "AARN0000001")

        resultOf(request) should containMessages("linkAccount.title", "error.no-radio-selected")
      }
    }

    "form value is invalid" should {
      "result in a BadRequest" in new RequestWithSessionDetails(autoMappingFormValue = "somethingInvalid") {
        a[BadRequestException] shouldBe thrownBy(resultOf(request))
      }
    }

    "ARN is missing from session" should {
      "redirect to /check-business-type" in {
        val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
          .withFormUrlEncodedBody("autoMapping" -> "yes")

        resultShouldBeSessionDataMissing(resultOf(request))
      }
    }
  }


  "showSubscriptionComplete" should {
    trait RequestWithSessionDetails {
      val arnInSession = "AARN0000001"
      implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
        .withSession("arn" -> arnInSession)
      sessionStoreService.currentSession.wasEligibleForMapping = Some(true)
    }
    def resultOf(request: Request[AnyContent]) = await(controller.showSubscriptionComplete(request))

    behave like anAgentAffinityGroupOnlyEndpoint(resultOf)

    behave like aPageWithFeedbackLinks(resultOf, new RequestWithSessionDetails{}.request)

    "display the ARN in a prettified format" in new RequestWithSessionDetails {
      val expectedPrettifiedArn = FieldMappings.prettify(Arn(arnInSession))
      expectedPrettifiedArn shouldBe "AARN-000-0001"
      resultOf(request) should containSubstrings(expectedPrettifiedArn)
    }

    "display the static page content" in new RequestWithSessionDetails {
      resultOf(request) should containMessages(
        "subscriptionComplete.title",
        "subscriptionComplete.h1",
        "subscriptionComplete.accountName",
        "subscriptionComplete.h2",
        "subscriptionComplete.p1",
        "subscriptionComplete.bullet-list.1",
        "subscriptionComplete.bullet-list.2"
      )
    }

    "selectively show linked account content" when {
      val containLinkedAccountMsgs = containMessages(
        "subscriptionComplete.link-account.h2",
        "subscriptionComplete.link-account.p1",
        "subscriptionComplete.link-account.p2",
        "subscriptionComplete.link-account.bullet-list.1",
        "subscriptionComplete.link-account.bullet-list.2",
        "subscriptionComplete.link-account.p3")

      "they were eligible for mapping, it should be shown" in new RequestWithSessionDetails {
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)
        resultOf(request) should containLinkedAccountMsgs
      }

      "they were not eligible for mapping, it should not be shown" in new RequestWithSessionDetails {
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)
        resultOf(request) shouldNot containLinkedAccountMsgs
      }

      "the mapping eligibility is unknown in the session store" in new RequestWithSessionDetails {
        sessionStoreService.currentSession.wasEligibleForMapping = None
        resultOf(request) shouldNot containLinkedAccountMsgs
      }
    }

    "redirect to session missing page" when {
      "the arn is missing from the session" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)
        resultShouldBeSessionDataMissing(resultOf(request))
      }
    }

    "tolerate a possible short delay in the new enrolment becoming visible in auth" when {
      "there was a delay and the new enrolment is not yet visible in auth" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withSession("arn" -> "AARN0000001")
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)

        resultOf(request) should containMessages("subscriptionComplete.title")
      }
      "there was no delay and the new enrolment is visible in auth" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
          .withSession("arn" -> "AARN0000001")
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)

        resultOf(request) should containMessages("subscriptionComplete.title")
      }
    }

    "contain a button to continue journey" when {
      "a continue URL exists in the session, show a generic 'Continue' button using that URL" in new RequestWithSessionDetails {
        val continueUrl = ContinueUrl("/test-continue-url")
        sessionStoreService.currentSession.continueUrl = Some(continueUrl)

        resultOf(request) should containSubstrings(
          s">${htmlEscapedMessage("subscriptionComplete.button.continueJourney")}</a>",
          continueUrl.url)
      }

      "no continue URL exists in the session, show a button with a link in AS services" in new RequestWithSessionDetails {
        sessionStoreService.currentSession.continueUrl = None

        resultOf(request) should containSubstrings(
          s">${htmlEscapedMessage("subscriptionComplete.button.continueToASAccount")}</a>",
          redirectUrl)
      }
    }

    "remove existing session" in new RequestWithSessionDetails {
      val result = resultOf(request)

      status(result) shouldBe 200
      sessionStoreService.allSessionsRemoved shouldBe true
      result.session.get("arn") shouldBe None
    }
  }

  "submitInitialDetails" should {
    "redisplay form" when {
      "name contains invalid characters" in {
        implicit val request = subscriptionDetailsRequest("name", Seq("name" -> "InvalidAgencyName!@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.agency-name.invalid")
      }

      "email is omitted" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> ""))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.email.empty")
      }

      "email has no text in the domain part" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local@"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.email")
      }

      "email does not contain an '@'" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "local"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.email")
      }

      "email has no text in the local part" in {
        implicit val request = subscriptionDetailsRequest("email", Seq("email" -> "@domain"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.email")
      }

      "telephone is invalid with numbers and words" in {
        implicit val request = subscriptionDetailsRequest("telephone", Seq("telephone" -> "02073457443fff"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title", "error.telephone.invalid")
      }
    }

    "redisplay form and log a warning - hidden fields should never be invalid because they were validated when originally entered" when {
      "known facts postcode is not valid" in {
        pending
        implicit val request = subscriptionDetailsRequest("knownFactsPostcode", Seq("knownFactsPostcode" -> "1AA AA1"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title")
        // add check for Logger(getClass).warn here
      }

      "utr is not valid" in {
        pending
        implicit val request = subscriptionDetailsRequest("utr", Seq("utr" -> "012345"))
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)

        val result = await(controller.submitInitialDetails(request))

        result should containMessages("subscriptionDetails.title")
        // add check for Logger(getClass).warn here
      }
    }

    "redirect back to /check-business-type" when {
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
      "all fields are supplied and was not eligible for mapping" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)

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
        result2.session.get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())
        metricShouldExistAndBeUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-Complete")
      }

      "all fields are supplied and was eligible for mapping" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)

        val result = await(controller.submitInitialDetails(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest())
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showLinkAccount().url

        verifySubscriptionRequestSent(subscriptionRequest())
        metricShouldExistAndBeUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success",
          "Count-Subscription-Complete")
      }

      "all fields are supplied but address contains more than 4 lines" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)
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
        result2.session.get("arn") shouldBe Some("ARN00001")

        verifySubscriptionRequestSent(subscriptionRequest())
        // add check for Logger(getClass).warn here
        metricShouldExistAndBeUpdated(
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

      val fakeRequest1 = subscriptionDetailsRequest()
      sessionStoreService.currentSession(hc(fakeRequest1)).wasEligibleForMapping = Some(false)

      val user1Result1 = await(controller.submitInitialDetails(fakeRequest1))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      val request2 = subscriptionRequest2()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request2, "ARN00002")

      val fakeRequest2 = subscriptionDetailsRequest2()
      sessionStoreService.currentSession(hc(fakeRequest2)).wasEligibleForMapping = Some(false)

      val user2Result1 = await(controller.submitInitialDetails(fakeRequest2))
      status(user2Result1) shouldBe 303
      redirectLocation(user2Result1).head shouldBe "/api/dummy/callback"

      stubAddressLookupReturnedAddress("addr1", request)
      val fakeRequest3 = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      val user1Result2 =
        await(controller.returnFromAddressLookup("addr1")(fakeRequest3))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
      verifySubscriptionRequestSent(request)

      sessionStoreService.allSessionsRemoved shouldBe false
      user1Result2.session(fakeRequest3).get("arn") shouldBe Some("ARN00001")

      stubAddressLookupReturnedAddress("addr2", request2)
      val fakeRequest4 = authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments)
      val user2Result2 = await(
        controller.returnFromAddressLookup("addr2")(fakeRequest4))
      verifySubscriptionRequestSent(request)

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      sessionStoreService.allSessionsRemoved shouldBe false
      user2Result2.session(fakeRequest4).get("arn") shouldBe Some("ARN00002")
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
        metricShouldExistAndBeUpdated(
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
        metricShouldExistAndBeUpdated(
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

        result should containMessages("error.postcode.blacklisted", "invalidAddress.title")
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

        result should containSubstrings(htmlEscapedMessage("error.maxLength", 35))
        result should containMessages("invalidAddress.title")
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
    "subscribe and redirect to /subscription-complete if there are no form errors" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

      implicit val request = desAddressForm()
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitModifiedAddress()(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

      verifySubscriptionRequestSent(subscriptionRequest())
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

        result should containSubstrings(htmlEscapedMessage("error.maxLength", 35),
          tooLongAddressLine)
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
