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

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AMLSDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub.givenNoSubscriptionJourneyRecordExists
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData._
import uk.gov.hmrc.play.binders.ContinueUrl

import scala.concurrent.ExecutionContext.Implicits.global

trait SubscriptionControllerISpec extends BaseISpec with SessionDataMissingSpec with ScalaFutures {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder

  protected val utr = Utr("2000000000")
  protected val knownFactsPostcode = "AA1 2AA"

  protected lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  protected lazy val redirectUrl = "https://www.gov.uk/"

  val amlsSDetails = AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now().plusDays(10))))

  val agentSession = Some(
    AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode("AA1 2AA")), registration = Some(testRegistration), amlsDetails = Some(amlsSDetails)))

  "showCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCheckAnswers(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/create-new-account")
      noMetricExpectedAtThisPoint()
    }

    "show subscription answers page if user has not already subscribed and has clean creds and also cache the goBack url" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = agentSession

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

      result should containSubstrings(registrationName, testRegistration.emailAddress.get, testRegistration.address.addressLine1, testRegistration.address.postalCode.get)

      sessionStoreService.fetchGoBackUrl.futureValue shouldBe Some(routes.SubscriptionController.showCheckAnswers().url)
    }
    "show subscription answers page without AMLS section if the agent is on the manually assured list" in new TestSetupNoJourneyRecord {
      givenAgentIsManuallyAssured(validUtr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = agentSession.map(session => session.copy(amlsDetails = None,
        taskListFlags = TaskListFlags(isMAA = true)))

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
      result should not(containMessages("checkAnswers.amlsDetails.pending.label"))
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))

      resultShouldBeSessionDataMissing(result)
    }

    "redirect to the amls /check-money-laundering-compliance page if amls details are missing in the session" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.agentSession = agentSession.map(session => session.copy(amlsDetails = None))

      val result = await(controller.showCheckAnswers(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsRegisteredPage().url)

    }
  }

  "submitCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitCheckAnswers(request))

    "redirect to unclean credentials page if user has enrolled in any other services" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.submitCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/create-new-account")
      noMetricExpectedAtThisPoint()
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.subscriptionWillConflict(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        val agentSession = AgentSession(Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode("AA1 2AA")), registration = Some(testRegistration.copy(isSubscribedToAgentServices = true)), amlsDetails = Some(amlsSDetails))

        sessionStoreService.currentSession.agentSession = Some(agentSession)

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
      val arn = "AARN0000001"
      AuthStub.authenticatedAgent(arn)
      implicit val request = FakeRequest()
      sessionStoreService.currentSession.agentSession = agentSession

    }
    def resultOf(request: Request[AnyContent]) = await(controller.showSubscriptionComplete(request))

    behave like aPageWithFeedbackLinks(resultOf, new RequestWithSessionDetails {}.request)

    "display the ARN in a raw format (without dashes)" in new RequestWithSessionDetails {
      val expectedArn = arn
      expectedArn shouldBe "AARN0000001"
      resultOf(request) should containSubstrings(expectedArn)
    }

    "display the static page content" in new RequestWithSessionDetails {

      val result = resultOf(request)

      result should containMessages(
        "subscriptionComplete.title",
        "subscriptionComplete.h1",
        "subscriptionComplete.accountName",
        "subscriptionComplete.h2",
        "subscriptionComplete.bullet-list.1",
        "subscriptionComplete.bullet-list.2"
      )
      bodyOf(result) should include(hasMessage("subscriptionComplete.p1", "AARN0000001"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.p2", "test@gmail.com"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.p3", "https://www.gov.uk/guidance/get-an-hmrc-agent-services-account"))
    }
    "continue button redirects to task list if the create task flag is true" in new RequestWithSessionDetails  {
      sessionStoreService.currentSession.agentSession = Some(agentSession.get.copy(taskListFlags = TaskListFlags(createTaskComplete = true)))
      val result = resultOf(request)
      result should containMessages(
        "subscriptionComplete.button.continueJourney"
      )
      checkHtmlResultWithBodyText(result, "agent-subscription/task-list")
    }
  }

  "returnFromAddressLookup" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessAddressForm(request))

    "redirect to check answers page" when {
      "all fields are supplied" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.agentSession = agentSession

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

        val updatedAddress = await(sessionStoreService.fetchAgentSession).get.registration.get.address
        updatedAddress.addressLine1 shouldBe subscriptionRequest().agency.address.addressLine1
        updatedAddress.addressLine2 shouldBe subscriptionRequest().agency.address.addressLine2
        updatedAddress.postalCode.get shouldBe subscriptionRequest().agency.address.postcode
        updatedAddress.countryCode shouldBe subscriptionRequest().agency.address.countryCode

        metricShouldExistAndBeUpdated("Count-Subscription-AddressLookup-Start", "Count-Subscription-AddressLookup-Success")
      }

      "all fields are supplied but address contains more than 4 lines" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.agentSession = agentSession

        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe "/api/dummy/callback"

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest(), Seq("Line 4", "Line 5"))
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
        metricShouldExistAndBeUpdated("Count-Subscription-AddressLookup-Start", "Count-Subscription-AddressLookup-Success")
      }

      "town is omitted" in new TestSetupNoJourneyRecord {
        implicit val request = subscriptionDetailsRequest()
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(town = None))
        sessionStoreService.currentSession.agentSession = agentSession

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

    "always send countryCode=GB to the back end as we do not currently allow non-UK addresses" in new TestSetupNoJourneyRecord {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(countryCode = "GB"))

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      implicit val detailsRequest = subscriptionDetailsRequest()
      sessionStoreService.currentSession.agentSession = agentSession

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

    "not mix up data from concurrent users" in new TestSetupNoJourneyRecord {
      givenNoSubscriptionJourneyRecordExists(AuthProviderId("54321-credId"))

      val request = subscriptionRequest()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

      givenAddressLookupInit("agents-subscr", "/api/dummy/callback")

      implicit val fakeRequest1 = subscriptionDetailsRequest()
      sessionStoreService.currentSession.agentSession = agentSession

      val user1Result1 = await(controller.showBusinessAddressForm(fakeRequest1))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe "/api/dummy/callback"

      val request2 = subscriptionRequest2()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request2, "ARN00002")

      val fakeRequest2 = subscriptionDetailsRequest2()
      sessionStoreService.currentSession(hc(fakeRequest2)).agentSession = agentSession

      val user2Result1 = await(controller.showBusinessAddressForm(fakeRequest2))
      status(user2Result1) shouldBe 303
      redirectLocation(user2Result1).head shouldBe "/api/dummy/callback"

      stubAddressLookupReturnedAddress("addr1", request)

      val fakeRequest3 = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession(hc(fakeRequest3)).agentSession = agentSession

      val user1Result2 =
        await(controller.returnFromAddressLookup("addr1")(fakeRequest3))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      sessionStoreService.allSessionsRemoved shouldBe false

      stubAddressLookupReturnedAddress("addr2", request2)
      val fakeRequest4 = authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession(hc(fakeRequest4)).agentSession = agentSession
      val user2Result2 = await(controller.returnFromAddressLookup("addr2")(fakeRequest4))

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      sessionStoreService.allSessionsRemoved shouldBe false
    }

    "display address_form_with_errors and report related errors" when {
      "postcode is blacklisted" in new TestSetupNoJourneyRecord {
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.agentSession = agentSession

        givenAddressLookupInit("agents-subscr", "/api/dummy/callback")
        val result0 = await(controller.showBusinessAddressForm(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe "/api/dummy/callback"

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10 1ZT")
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        result should containMessages("error.postcode.blacklisted", "invalidAddress.title")
      }

      "the address is not valid according to DES's rules" in new TestSetupNoJourneyRecord {
        implicit val request = subscriptionDetailsRequest()
        sessionStoreService.currentSession.agentSession = agentSession

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

    "redirect to the business-type page if there is no initial details in session because the user has returned to a bookmark" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      givenAddressLookupReturnsAddress("addr1")
      val result = await(controller.returnFromAddressLookup("addr1")(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitModifiedAddress" should {
    "subscribe and redirect to /check-answers if there are no form errors" in new TestSetupNoJourneyRecord {
      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

      implicit val request = desAddressForm()
      sessionStoreService.currentSession.agentSession = agentSession

      val result = await(controller.submitModifiedAddress()(request))

      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "redirect to /business-type if there is no valid session" in new TestSetupNoJourneyRecord {
      implicit val request = desAddressForm()
      val result = await(controller.submitModifiedAddress()(request))

      resultShouldBeSessionDataMissing(result)
      noMetricExpectedAtThisPoint()
    }

    "redisplay address_form_with_errors and show errors" when {
      "the address is not valid according to DES's rules" in new TestSetupNoJourneyRecord {
        val tooLongAddressLine = "12345678901234567890123456789012345678901234567890"
        implicit val request = desAddressForm(addressLine1 = tooLongAddressLine)
        sessionStoreService.currentSession.agentSession = agentSession

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

  private def subscriptionDetailsRequest(keyToRemove: String = "", additionalParameters: Seq[(String, String)] = Seq()) =
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
      ),
      amlsDetails = Some(AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))))


  protected def subscriptionRequestWithNoEdit() =
    SubscriptionRequest(
      utr = validUtr,
      knownFacts = SubscriptionRequestKnownFacts(knownFactsPostcode),
      agency = Agency(
        name = registrationName,
        address = DesAddress(
          addressLine1 = testRegistration.address.addressLine1,
          addressLine2 = testRegistration.address.addressLine2,
          addressLine3 = testRegistration.address.addressLine3,
          addressLine4 = testRegistration.address.addressLine4,
          postcode = testRegistration.address.postalCode.get,
          countryCode = testRegistration.address.countryCode
        ),
        email = testRegistration.emailAddress.get
      ),
      amlsDetails = Some(amlsSDetails)
    )

  private def subscriptionDetailsRequest2(keyToRemove: String = "", additionalParameters: Seq[(String, String)] = Seq()) =
    authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
      Seq("utr" -> utr.value, "knownFactsPostcode" -> "BA1 2AA", "name" -> "My Agency 2", "email" -> "agency2@example.com", "telephone" -> "0123 456 7899")
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
      ),
      amlsDetails = Some(AMLSDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now()))))
    )

  private def stubAddressLookupReturnedAddress(addressId: String, subscriptionRequest: SubscriptionRequest, unsupportedAddressLines: Seq[String] = Seq.empty) =
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

class SubscriptionControllerTests extends SubscriptionControllerISpec {

  "submitCheckAnswers" should {
    "send subscription request and redirect to subscription complete when there is a continue url" when {
      "all fields are supplied" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit(), arn = "TARN00023")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession = agentSession
        sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/some/url"))

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }

      "some fields are supplied" in new TestSetupNoJourneyRecord {
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession = agentSession
        sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/some/url"))

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }

      "amlsDetails are passed in" in new TestSetupNoJourneyRecord{
        val amlsDetails = Some(AMLSDetails("supervisory", Right(RegisteredDetails("123", LocalDate.now()))))
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.agentSession = agentSession
        sessionStoreService.currentSession.continueUrl = Some(ContinueUrl("/some/url"))
        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }
    }
  }
}
