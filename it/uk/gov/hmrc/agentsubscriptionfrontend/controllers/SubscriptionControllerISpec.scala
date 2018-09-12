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
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, MappingStubs}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TaxIdentifierFormatters}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

trait SubscriptionControllerISpec extends BaseISpec with SessionDataMissingSpec {
  protected def featureFlagAutoMapping: Boolean

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure("features.auto-map-agent-enrolments" -> featureFlagAutoMapping)

  protected val utr = Utr("2000000000")
  protected val knownFactsPostcode = "AA1 2AA"
  protected  val myAgencyKnownFactsResult =
    KnownFactsResult(
      utr = utr,
      postcode = knownFactsPostcode,
      taxpayerName = "My Business",
      isSubscribedToAgentServices = false,
      Some(
        BusinessAddress(
          "AddressLine1 A",
          Some("AddressLine2 A"),
          Some("AddressLine3 A"),
          Some("AddressLine4 A"),
          Some("AA11AA"),
          "GB")),
      Some("someone@example.com")
    )

  protected  val initialDetails =
    InitialDetails(
      utr,
      knownFactsPostcode,
      "My Agency",
      Some("agency@example.com"),
      BusinessAddress(
        "AddressLine1 A",
        Some("AddressLine2 A"),
        Some("AddressLine3 A"),
        Some("AddressLine4 A"),
        Some("AA11AA"),
        "GB")
    )

  protected lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  protected lazy val redirectUrl = "https://www.gov.uk/"

  "showCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCheckAnswers(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/create-new-account")
      noMetricExpectedAtThisPoint()
    }

    "show subscription answers page if user has not already subscribed and has clean creds" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.showCheckAnswers(request))
      result should containMessages(
        "checkAnswers.title",
        "checkAnswers.description.p1",
        "checkAnswers.description.p2",
        "checkAnswers.change.button",
        "checkAnswers.confirm.button",
        "checkAnswers.businessName.label",
        "checkAnswers.businessEmailAddress.label",
        "checkAnswers.businessAddress.label"
      )

      result should containSubstrings(
        initialDetails.name,
        initialDetails.email.get,
        initialDetails.businessAddress.addressLine1,
        initialDetails.businessAddress.postalCode.get)
      metricShouldExistAndBeUpdated("Count-Subscription-CleanCreds-Success")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitCheckAnswers(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.submitCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/create-new-account")
      noMetricExpectedAtThisPoint()
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in {
        AgentSubscriptionStub.subscriptionWillConflict(utr, subscriptionRequestWithNoEdit(initialDetails))

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)

        val result = await(controller.submitCheckAnswers(request))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showAlreadySubscribed().url
        sessionStoreService.allSessionsRemoved shouldBe false
        metricShouldExistAndBeUpdated(
          "Count-Subscription-AlreadySubscribed-APIResponse",
          "Http4xxErrorCount-ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST"
        )
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

    behave like aPageWithFeedbackLinks(resultOf, new RequestWithSessionDetails {}.request)

    "display the ARN in a prettified format" in new RequestWithSessionDetails {
      val expectedPrettifiedArn = TaxIdentifierFormatters.prettify(Arn(arnInSession))
      expectedPrettifiedArn shouldBe "AARN-000-0001"
      resultOf(request) should containSubstrings(expectedPrettifiedArn)
    }

    "display the static page content" in new RequestWithSessionDetails {
      resultOf(request) should containMessages(
        "subscriptionComplete.title",
        "subscriptionComplete.h1",
        "subscriptionComplete.accountName",
        "subscriptionComplete.h2",
        "subscriptionComplete.bullet-list.1",
        "subscriptionComplete.bullet-list.2"
      )

      bodyOf(resultOf(request)) should include(hasMessage("subscriptionComplete.p1", "AARN-000-0001"))
      bodyOf(resultOf(request)) should include(hasMessage("subscriptionComplete.p2", "https://www.gov.uk/guidance/get-an-hmrc-agent-services-account"))



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

  "returnFromAddressLookup" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessAddressForm(request))

    "redirect to check answers page" when {
      "all fields are supplied" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest())
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
        sessionStoreService.allSessionsRemoved shouldBe false

        val updatedAddress = await(sessionStoreService.fetchInitialDetails).get.businessAddress
        updatedAddress.addressLine1 shouldBe subscriptionRequest().agency.address.addressLine1
        updatedAddress.addressLine2 shouldBe subscriptionRequest().agency.address.addressLine2
        updatedAddress.postalCode.get shouldBe subscriptionRequest().agency.address.postcode
        updatedAddress.countryCode shouldBe subscriptionRequest().agency.address.countryCode

        metricShouldExistAndBeUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success")
      }

      "all fields are supplied but address contains more than 4 lines" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest(), Seq("Line 4", "Line 5"))
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
        metricShouldExistAndBeUpdated(
          "Count-Subscription-AddressLookup-Start",
          "Count-Subscription-AddressLookup-Success")
      }

      "town is omitted" in {
        implicit val request = subscriptionDetailsRequest()
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(town = None))
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest(town = None))
        val result2 =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
      }
    }

    "always send countryCode=GB to the back end as we do not currently allow non-UK addresses" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(countryCode = "GB"))

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      implicit val detailsRequest = subscriptionDetailsRequest()
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.showBusinessAddressForm(detailsRequest))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe "/api/dummy/callback"

      val addressId = "addr1"
      stubAddressLookupReturnedAddress(addressId, subscriptionRequest(countryCode = "AR"))
      val result2 =
        await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      status(result2) shouldBe 303
      redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "not mix up data from concurrent users" in {
      val request = subscriptionRequest()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      val fakeRequest1 = subscriptionDetailsRequest()
      sessionStoreService.currentSession(hc(fakeRequest1)).wasEligibleForMapping = Some(false)
      sessionStoreService.currentSession(hc(fakeRequest1)).initialDetails = Some(initialDetails)

      val user1Result1 = await(controller.showBusinessAddressForm(fakeRequest1))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      val request2 = subscriptionRequest2()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request2, "ARN00002")

      val fakeRequest2 = subscriptionDetailsRequest2()
      sessionStoreService.currentSession(hc(fakeRequest2)).wasEligibleForMapping = Some(false)
      sessionStoreService.currentSession(hc(fakeRequest2)).initialDetails = Some(initialDetails)

      val user2Result1 = await(controller.showBusinessAddressForm(fakeRequest2))
      status(user2Result1) shouldBe 303
      redirectLocation(user2Result1).head shouldBe "/api/dummy/callback"

      stubAddressLookupReturnedAddress("addr1", request)
      val fakeRequest3 = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession(hc(fakeRequest3)).initialDetails = Some(initialDetails)
      val user1Result2 =
        await(controller.returnFromAddressLookup("addr1")(fakeRequest3))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      sessionStoreService.allSessionsRemoved shouldBe false

      stubAddressLookupReturnedAddress("addr2", request2)
      val fakeRequest4 = authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession(hc(fakeRequest4)).initialDetails = Some(initialDetails)
      val user2Result2 = await(controller.returnFromAddressLookup("addr2")(fakeRequest4))

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      sessionStoreService.allSessionsRemoved shouldBe false
    }

    "display address_form_with_errors and report related errors" when {
      "postcode is blacklisted" in {
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.showBusinessAddressForm(request))
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
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.showBusinessAddressForm(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        val tooLongLine = "123456789012345678901234567890123456"
        givenAddressLookupReturnsAddress("addr1", addressLine1 = tooLongLine)
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        result should containSubstrings(htmlEscapedMessage("error.addressline.1.maxlength", 35))
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
    "subscribe and redirect to /check-answers if there are no form errors" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

      implicit val request = desAddressForm()
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitModifiedAddress()(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "redirect to /business-type if there is no valid session" in {
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
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.submitModifiedAddress()(request))

        result should containSubstrings(htmlEscapedMessage("error.addressline.1.maxlength", 35), tooLongAddressLine)
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
    postcode: String = "AA11AA",
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
        email = "agency@example.com"
      )
    )

  protected def subscriptionRequestWithNoEdit(initialDetails: InitialDetails) =
    SubscriptionRequest(
      utr = initialDetails.utr,
      knownFacts = SubscriptionRequestKnownFacts(knownFactsPostcode),
      agency = Agency(
        name = initialDetails.name,
        address = DesAddress(
          addressLine1 = initialDetails.businessAddress.addressLine1,
          addressLine2 = initialDetails.businessAddress.addressLine2,
          addressLine3 = initialDetails.businessAddress.addressLine3,
          addressLine4 = initialDetails.businessAddress.addressLine4,
          postcode = initialDetails.businessAddress.postalCode.get,
          countryCode = initialDetails.businessAddress.countryCode
        ),
        email = initialDetails.email.get
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
        email = "agency2@example.com"
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

  protected def verifySubscriptionRequestSent(subscriptionRequest: SubscriptionRequest) =
    verify(
      postRequestedFor(urlEqualTo("/agent-subscription/subscription"))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine1))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine2.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine3.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.addressLine4.getOrElse("")))
        .withRequestBody(containing(subscriptionRequest.agency.address.postcode))
        .withRequestBody(containing(subscriptionRequest.agency.address.countryCode)))
}

class SubscriptionControllerWithAutoMappingOn extends SubscriptionControllerISpec {
  override protected def featureFlagAutoMapping: Boolean = true

  "submitCheckAnswers" should {
    "send subscription request and redirect to subscription complete" when {
      "all fields are supplied and was not eligible for mapping" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequestWithNoEdit(initialDetails), arn = "TARN00023")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)
        sessionStoreService.currentSession.wasEligibleForMapping = Some(false)

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url
        result.session.get("arn") shouldBe Some("TARN00023")

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit(initialDetails))
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }

      "all fields are supplied and was eligible for mapping" in {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequestWithNoEdit(initialDetails))

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)
        sessionStoreService.currentSession.wasEligibleForMapping = Some(true)

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showLinkClients().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit(initialDetails))
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }
    }

    "showLinkClients (GET /link-clients)" should {
      trait RequestAndResult {
        val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
          .withSession("arn" -> "AARN0000001")
        val result = await(controller.showLinkClients(request))
        val doc = Jsoup.parse(bodyOf(result))
      }

      behave like anAgentAffinityGroupOnlyEndpoint(controller.showLinkClients(_))

      "contain page titles and content" in new RequestAndResult {
        result should containMessages(
          "linkClients.title",
          "linkClients.p1",
          "linkClients.p2",
          "linkClients.bullet-list.1",
          "linkClients.bullet-list.2",
          "linkClients.p3",
          "linkClients.p4",
          "linkClients.legend",
          "linkClients.option.yes",
          "linkClients.option.no"
        )
      }

      "contain radio options for Yes and No" in new RequestAndResult {
        // Check form's radio inputs have correct values
        doc.getElementById("autoMapping-yes").`val`() shouldBe "yes"
        doc.getElementById("autoMapping-no").`val`() shouldBe "no"
      }

      "form should POST to /link-clients" in new RequestAndResult {
        val form = doc.select("form").first()
        form.attr("method") shouldBe "POST"
        form.attr("action") shouldBe routes.SubscriptionController.submitLinkClients().url
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
          val result = await(controller.showLinkClients(request))
          result should containMessages("linkClients.title")
        }
        "there was no delay and the new enrolment is visible in auth" in {
          val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT).withSession("arn" -> "AARN0000001")
          val result = await(controller.showLinkClients(request))
          result should containMessages("linkClients.title")
        }
      }

      "redirect to /business-type if subscribed arn is missing from session" in {
        val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        resultShouldBeSessionDataMissing(await(controller.showLinkClients(request)))
      }
    }

    "submitLinkClients (POST /link-clients)" when {
      class RequestWithSessionDetails(autoMappingFormValue: String) {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
          .withFormUrlEncodedBody("autoMapping" -> autoMappingFormValue)
          .withSession("arn" -> "AARN0000001")
        sessionStoreService.currentSession.knownFactsResult = Some(myAgencyKnownFactsResult)
      }

      def resultOf(request: Request[AnyContent]) = await(controller.submitLinkClients(request))

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
        "return 200 and redisplay the /link-clients page with an error message for missing choice" in {
          val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
            .withSession("arn" -> "AARN0000001")

          resultOf(request) should containMessages("linkClients.title", "linkClients.error.no-radio-selected")
        }
      }

      "form value is invalid" should {
        "result in a BadRequest" in new RequestWithSessionDetails(autoMappingFormValue = "somethingInvalid") {
          a[BadRequestException] shouldBe thrownBy(resultOf(request))
        }
      }

      "ARN is missing from session" should {
        "redirect to /business-type" in {
          val request = authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)
            .withFormUrlEncodedBody("autoMapping" -> "yes")

          resultShouldBeSessionDataMissing(resultOf(request))
        }
      }
    }
  }
}

class SubscriptionControllerWithAutoMappingOff extends SubscriptionControllerISpec {
  override protected def featureFlagAutoMapping: Boolean = false

  "submitCheckAnswers" should {
    "send subscription request and redirect to subscription complete when all fields are supplied and was not eligible for mapping" in {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequestWithNoEdit(initialDetails), arn = "TARN00023")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)
      sessionStoreService.currentSession.wasEligibleForMapping = None

      val result = await(controller.submitCheckAnswers(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showSubscriptionComplete().url)
      result.session.get("arn") shouldBe Some("TARN00023")

      verifySubscriptionRequestSent(subscriptionRequestWithNoEdit(initialDetails))
      metricShouldExistAndBeUpdated("Count-Subscription-Complete")
    }
  }

  "showLinkClients (GET /link-clients)" should {
    "500 internal server error" in {
      val result = await(controller.showLinkClients(FakeRequest()))
      status(result) shouldBe 500
    }
  }

  "submitLinkClients (POST /link-clients)" should {
    "500 internal server error" in {
      val result = await(controller.submitLinkClients(FakeRequest()))
      status(result) shouldBe 500
    }
  }
}