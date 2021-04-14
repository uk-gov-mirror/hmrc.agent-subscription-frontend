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
import play.api.i18n.Lang
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Cookie, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.models.{AmlsDetails, _}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.{AgentSubscriptionStub, AuthStub}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{utr, _}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestSetupNoJourneyRecord}

import scala.concurrent.ExecutionContext.Implicits.global

trait TestSetupWithCompleteJourneyRecord {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordNoMappings)
  givenAgentIsNotManuallyAssured(utr.value)
}

trait TestSetupWithCouldBePartiallySubscribedRecord {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), couldBePartiallySubscribedJourneyRecord)
  givenAgentIsNotManuallyAssured(utr.value)
}

trait TestSetupWithMinimalSubscriptionJourneyRecord {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), minimalSubscriptionJourneyRecord(AuthProviderId("12345-credId")))
  givenAgentIsNotManuallyAssured(utr.value)
}

trait TestSetupWithMinimalSubscriptionJourneyRecordAndRegistration {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordNoMappings.copy(amlsData = None))
  givenAgentIsNotManuallyAssured(utr.value)
}

trait TestSetupWithCompleteJourneyRecordWithMapping {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordWithMappings)
  givenAgentIsNotManuallyAssured(utr.value)
}

trait TestSetupWithCompleteJourneyRecordAndCreate {
  givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordNoMappings)
  givenAgentIsNotManuallyAssured(utr.value)
  givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings)
}

class SubscriptionControllerISpec extends BaseISpec with SessionDataMissingSpec with ScalaFutures {

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder

  protected val utr = Utr("2000000000")
  protected val knownFactsPostcode = "AA1 2AA"

  protected val returnFromAddressLookupUrl: String = "/agent-subscription/return-from-address-lookup"
  protected val showBusinessAddressFormUrl: String = "/agent-subscription/business-address"

  protected lazy val controller: SubscriptionController = app.injector.instanceOf[SubscriptionController]

  protected lazy val redirectUrl = "https://www.gov.uk/"

  val amlsDetails = AmlsDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now().plusDays(10))))

  val agentSession = Some(
    AgentSession(
      Some(BusinessType.SoleTrader),
      utr = Some(validUtr),
      postcode = Some(Postcode("AA1 2AA")),
      registration = Some(testRegistration)))

  "showCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCheckAnswers(request))

    "redirect to sign in with a new user ID page if user has enrolled in any other services" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/sign-in-with-new-user-id")
      noMetricExpectedAtThisPoint()
    }

    "redirect to /business-type if no registration data found" in new TestSetupWithMinimalSubscriptionJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.BusinessTypeController.showBusinessTypeForm().url)
      noMetricExpectedAtThisPoint()
    }

    "redirect to /check-money-laundering-compliance if not manually assured and no amls data found" in
      new TestSetupWithMinimalSubscriptionJourneyRecordAndRegistration {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.AMLSController.showAmlsRegisteredPage().url)
      noMetricExpectedAtThisPoint()
    }

    "redirect to /contact-email-check if no contact email address found" in {

      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordWithMappings.copy(contactEmailData = None))
      givenAgentIsNotManuallyAssured(utr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailCheck().url)
      noMetricExpectedAtThisPoint()
    }

    "redirect to /trading-name if no contact trading name data found" in {

      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordWithMappings.copy(contactTradingNameData = None))
      givenAgentIsNotManuallyAssured(utr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTradingNameCheck().url)
      noMetricExpectedAtThisPoint()
    }

    "redirect to /check-main-trading-address if no contact trading address data found" in {

      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordWithMappings.copy(contactTradingAddressData = None))
      givenAgentIsNotManuallyAssured(utr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showCheckMainTradingAddress().url)
      noMetricExpectedAtThisPoint()
    }



    "show subscription answers page if user has not already subscribed and has clean creds and also cache the goBack url" in
      new TestSetupWithCompleteJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      result should containMessages(
        "checkAnswers.title",
        "checkAnswers.description.p3",
        "checkAnswers.change.button",
        "checkAnswers.confirm.button",
        "checkAnswers.contactEmailAddress.label",
        "checkAnswers.contactTradingName.label",
        "checkAnswers.contactTradingAddress.label",
        "checkAnswers.amls.h2",
        "checkAnswers.contactDetails.h2"

      )

      result shouldNot containMessages("checkAnswers.userMapping.label", "checkAnswers.mapping.h2")

      result should containSubstrings(
        testRegistration.address.addressLine1, testRegistration.address.postalCode.get)

      sessionStoreService.fetchGoBackUrl.futureValue shouldBe Some(routes.SubscriptionController.showCheckAnswers().url)
    }

    "show subscription answers page without AMLS section if the agent is on the manually assured list" in new TestSetupWithCompleteJourneyRecord {
      givenAgentIsManuallyAssured(validUtr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments
      )

      val result = await(controller.showCheckAnswers(request))
      result should containMessages(
        "checkAnswers.title",
        "checkAnswers.change.button",
        "checkAnswers.confirm.button",
        "checkAnswers.contactEmailAddress.label",
        "checkAnswers.contactTradingName.label",
        "checkAnswers.contactTradingAddress.label",
        "checkAnswers.contactDetails.h2"
      )
      result should not(containMessages("checkAnswers.userMapping.label", "checkAnswers.mapping.h2"))
      result should not(containMessages("checkAnswers.ggId.label"))
      result should not(containMessages("checkAnswers.amlsDetails.pending.label", "checkAnswers.amls.h2"))
    }

    "show subscription answers page with mapping " in {
      givenSubscriptionJourneyRecordExists(AuthProviderId("12345-credId"), completeJourneyRecordWithMappings.copy(continueId = Some("continue-id")))
      givenAgentIsManuallyAssured(validUtr.value)
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showCheckAnswers(request))
      result should containMessages(
        "checkAnswers.title",
        "checkAnswers.change.button",
        "checkAnswers.confirm.button",
        "checkAnswers.userMapping.label",
        "checkAnswers.ggId.label",
        "checkAnswers.contactEmailAddress.label",
        "checkAnswers.contactTradingName.label",
        "checkAnswers.contactTradingAddress.label",
        "checkAnswers.mapping.h2",
        "checkAnswers.contactDetails.h2"
      )

      result should not(containMessages("checkAnswers.amlsDetails.pending.label", "checkAnswers.amls.h2"))
      checkHtmlResultWithBodyText(result, "XXXX-XXXX-1234", "XXXX-XXXX-5678")
    }

    "throw an exception when there is no continue url in the record" in new TestSetupWithCompleteJourneyRecordWithMapping {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      intercept[RuntimeException] {
        await(controller.showCheckAnswers(request))
      }.getMessage shouldBe "no continueId found in record"
    }
  }

  "submitCheckAnswers" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitCheckAnswers(request))

    "redirect to sign in with a new user id page if user has enrolled in any other services" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.submitCheckAnswers(request))
      status(result) shouldBe 303
      result.header.headers("Location") should include("/agent-subscription/sign-in-with-new-user-id")
      noMetricExpectedAtThisPoint()
    }

    "redirect to already subscribed" when {
      "agency is already subscribed to MTD" in new TestSetupWithCompleteJourneyRecord {
        AgentSubscriptionStub.subscriptionWillConflict(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withCookies(Cookie("PLAY_LANG", "en"))

        val result = await(controller.submitCheckAnswers(request))

        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showAlreadySubscribed().url
        metricShouldExistAndBeUpdated(
          "Count-Subscription-AlreadySubscribed-APIResponse",
          "Http4xxErrorCount-ConsumedAPI-Agent-Subscription-subscribeAgencyToMtd-POST"
        )
      }
    }
  }

  "showSubscriptionComplete" should {
    trait AuthRequest {
      val arn = "AARN0000001"
      AuthStub.authenticatedAgent(arn, "12345-credId")

      implicit val request = FakeRequest()
    }
    def resultOf(request: Request[AnyContent]) = await(controller.showSubscriptionComplete(request))

    behave like aPageWithFeedbackLinks(resultOf, new AuthRequest {}.request)

    "display the ARN in a raw format (without dashes)" in new AuthRequest with TestSetupWithCompleteJourneyRecord {
      val expectedArn = arn
      expectedArn shouldBe "AARN0000001"
      resultOf(request) should containSubstrings(expectedArn)
    }

    "display the static page content" in new AuthRequest with TestSetupWithCompleteJourneyRecordWithMapping {

      val result = resultOf(request)

      result should containMessages(
        "subscriptionComplete.title",
        "subscriptionComplete.h1",
        "subscriptionComplete.accountName",
        "subscriptionComplete.nextStepsMTD.header",
        "subscriptionComplete.nextStepsMTD.p",
        "subscriptionComplete.nextStepsTERS.header",
        "subscriptionComplete.nextStepsTERS.p",
        "subscriptionComplete.button.continueToASAccount"
      )
      bodyOf(result) should include(hasMessage("subscriptionComplete.p1", "AARN0000001"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.p2", "email@email.com"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.copiedAcross", 40))

    }

    "display the correct content when the user is partially-subscribed (has skipped task-list)" in new AuthRequest
      with TestSetupWithCouldBePartiallySubscribedRecord {
      val result = resultOf(request)

      result should containMessages(
        "subscriptionComplete.title",
        "subscriptionComplete.h1",
        "subscriptionComplete.accountName",
        "subscriptionComplete.nextStepsMTD.header",
        "subscriptionComplete.nextStepsMTD.p",
        "subscriptionComplete.nextStepsTERS.header",
        "subscriptionComplete.nextStepsTERS.p",
        "subscriptionComplete.button.continueToASAccount"
      )
      bodyOf(result) should include(hasMessage("subscriptionComplete.p1", "AARN0000001"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.p2", "test@gmail.com"))
      bodyOf(result) should include(hasMessage("subscriptionComplete.copiedAcross", 0))

    }


    "continue button redirects to agent services account" in new AuthRequest with TestSetupWithCompleteJourneyRecord {
      val result = resultOf(request)
      result should containMessages(
        "subscriptionComplete.button.continueToASAccount"
      )
      checkHtmlResultWithBodyText(result, "/agent-services-account")
    }
  }

  "returnFromAddressLookup" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessAddressForm(request))

    "redirect to check answers page" when {
      "all fields are supplied" in new TestSetupWithCompleteJourneyRecord {
        givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings.copy(
          businessDetails = completeJourneyRecordNoMappings.businessDetails.copy(
            registration = Some(completeJourneyRecordNoMappings.businessDetails.registration.get.copy(
              address = BusinessAddress(addressLine1 = "1 Some Street",
                addressLine2 = Some("Sometown"),
                addressLine3 = Some("County"),
                addressLine4 = Some("Address Line 4"),
                postalCode = Some("AA11AA"),
                countryCode = "GB")
            ))
          )
        ))
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit(returnFromAddressLookupUrl)

        implicit val request = subscriptionDetailsRequest()

        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe returnFromAddressLookupUrl

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest())
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

        metricShouldExistAndBeUpdated("Count-Subscription-AddressLookup-Start", "Count-Subscription-AddressLookup-Success")
      }

      "all fields are supplied but address contains more than 4 lines" in new TestSetupWithCompleteJourneyRecord {
        givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings.copy(
          businessDetails = completeJourneyRecordNoMappings.businessDetails.copy(
            registration = Some(completeJourneyRecordNoMappings.businessDetails.registration.get.copy(
              address = BusinessAddress(addressLine1 = "1 Some Street",
                addressLine2 = Some("Sometown"),
                addressLine3 = Some("County"),
                addressLine4 = Some("Address Line 4"),
                postalCode = Some("AA11AA"),
                countryCode = "GB")
            ))
          )
        ))
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest())

        givenAddressLookupInit(returnFromAddressLookupUrl)

        implicit val request = subscriptionDetailsRequest()

        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe returnFromAddressLookupUrl

        val addressId = "addr1"
        stubAddressLookupReturnedAddress(addressId, subscriptionRequest(), Seq("Line 4", "Line 5"))
        val result2 =
          await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
        metricShouldExistAndBeUpdated("Count-Subscription-AddressLookup-Start", "Count-Subscription-AddressLookup-Success")
      }

      "town is omitted" in new TestSetupWithCompleteJourneyRecord {
        givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings.copy(
          businessDetails = completeJourneyRecordNoMappings.businessDetails.copy(
            registration = Some(completeJourneyRecordNoMappings.businessDetails.registration.get.copy(
              address = BusinessAddress(addressLine1 = "1 Some Street",
                addressLine2 = None,
                addressLine3 = Some("County"),
                addressLine4 = Some("Address Line 4"),
                postalCode = Some("AA11AA"),
                countryCode = "GB")
            ))
          )
        ))

        implicit val request = subscriptionDetailsRequest()
        AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(town = None))

        givenAddressLookupInit(returnFromAddressLookupUrl)
        val result = await(controller.showBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe returnFromAddressLookupUrl

        stubAddressLookupReturnedAddress("addr1", subscriptionRequest(town = None))
        val result2 =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        status(result2) shouldBe 303
        redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
      }
    }

    "always send countryCode=GB to the back end as we do not currently allow non-UK addresses" in new TestSetupWithCompleteJourneyRecord {
      givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings.copy(
        businessDetails = completeJourneyRecordNoMappings.businessDetails.copy(
          registration = Some(completeJourneyRecordNoMappings.businessDetails.registration.get.copy(
            address = BusinessAddress(addressLine1 = "1 Some Street",
              addressLine2 = Some("Sometown"),
              addressLine3 = Some("County"),
              addressLine4 = Some("Address Line 4"),
              postalCode = Some("AA11AA"),
              countryCode = "AR")
          ))
        )
      ))

      AgentSubscriptionStub.subscriptionWillSucceed(utr, subscriptionRequest(countryCode = "GB"))

      givenAddressLookupInit(returnFromAddressLookupUrl)

      implicit val detailsRequest = subscriptionDetailsRequest()

      val result = await(controller.showBusinessAddressForm(detailsRequest))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe returnFromAddressLookupUrl

      val addressId = "addr1"
      stubAddressLookupReturnedAddress(addressId, subscriptionRequest(countryCode = "AR"))
      val result2 =
        await(controller.returnFromAddressLookup(addressId)(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      status(result2) shouldBe 303
      redirectLocation(result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "not mix up data from concurrent users" in new TestSetupWithCompleteJourneyRecord {
      givenSubscriptionRecordCreated(id, completeJourneyRecordNoMappings.copy(
        businessDetails = completeJourneyRecordNoMappings.businessDetails.copy(
          registration = Some(completeJourneyRecordNoMappings.businessDetails.registration.get.copy(
            address = BusinessAddress(addressLine1 = "1 Some Street",
              addressLine2 = Some("Sometown"),
              addressLine3 = Some("County"),
              addressLine4 = Some("Address Line 4"),
              postalCode = None,
              countryCode = "GB")
          ))
        )
      ))
      givenSubscriptionJourneyRecordExists(AuthProviderId("54321-credId"), completeJourneyRecordNoMappings)


      val request = subscriptionRequest()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request)

      givenAddressLookupInit(returnFromAddressLookupUrl)

      implicit val fakeRequest1 = subscriptionDetailsRequest()

      val user1Result1 = await(controller.showBusinessAddressForm(fakeRequest1))
      status(user1Result1) shouldBe 303
      redirectLocation(user1Result1).head shouldBe returnFromAddressLookupUrl

      val request2 = subscriptionRequest2()
      AgentSubscriptionStub.subscriptionWillSucceed(utr, request2, "ARN00002")

      val fakeRequest2 = subscriptionDetailsRequest2()

      val user2Result1 = await(controller.showBusinessAddressForm(fakeRequest2))
      status(user2Result1) shouldBe 303
      redirectLocation(user2Result1).head shouldBe returnFromAddressLookupUrl

      stubAddressLookupReturnedAddress("addr1", request)

      val fakeRequest3 = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val user1Result2 =
        await(controller.returnFromAddressLookup("addr1")(fakeRequest3))

      status(user1Result2) shouldBe 303
      redirectLocation(user1Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url

      stubAddressLookupReturnedAddress("addr2", request2)
      val fakeRequest4 = authenticatedAs(user = subscribing2ndCleanAgentWithoutEnrolments)
      val user2Result2 = await(controller.returnFromAddressLookup("addr2")(fakeRequest4))

      status(user2Result2) shouldBe 303
      redirectLocation(user2Result2).head shouldBe routes.SubscriptionController.showCheckAnswers().url
    }

    "display address_form_with_errors and report related errors" when {
      "postcode is blacklisted" in new TestSetupWithCompleteJourneyRecord {
        implicit val request = subscriptionDetailsRequest()

        givenAddressLookupInit(returnFromAddressLookupUrl)
        val result0 = await(controller.showBusinessAddressForm(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe returnFromAddressLookupUrl

        givenAddressLookupReturnsAddress("addr1", postcode = "AB10 1ZT")
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        result should containMessages("error.postcode.blacklisted", "invalidAddress.title")
      }

      "the address is not valid according to DES's rules" in new TestSetupWithCompleteJourneyRecord {
        implicit val request = subscriptionDetailsRequest()

        givenAddressLookupInit(returnFromAddressLookupUrl)
        val result0 = await(controller.showBusinessAddressForm(request))
        status(result0) shouldBe 303
        redirectLocation(result0).head shouldBe returnFromAddressLookupUrl

        val tooLongLine = "123456789012345678901234567890123456"
        givenAddressLookupReturnsAddress("addr1", addressLine1 = tooLongLine)
        val result =
          await(controller.returnFromAddressLookup("addr1")(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

        result should containSubstrings(htmlEscapedMessage("error.addressline.1.maxlength", 35))
        result should containMessages("invalidAddress.title")
      }
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

  "submitCheckAnswers" should {
    "send subscription request and redirect to subscription complete when there is a continue url" when {
      "all fields are supplied" in new TestSetupWithCompleteJourneyRecordAndCreate {
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit(), arn = "TARN00023")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.continueUrl = Some("/some/url")

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }

      "some fields are supplied" in new TestSetupWithCompleteJourneyRecordAndCreate {
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.continueUrl = Some("/some/url")

        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }

      "amlsDetails are passed in" in new TestSetupWithCompleteJourneyRecordAndCreate {
        AgentSubscriptionStub.subscriptionWillSucceed(validUtr, subscriptionRequestWithNoEdit())

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.continueUrl = Some("/some/url")
        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.SubscriptionController.showSubscriptionComplete().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Complete")
      }
    }

    "send subscription request and redirect to showCannotCreateAccount" when {
      "agent has been terminated" in new TestSetupWithCompleteJourneyRecordAndCreate {
        AgentSubscriptionStub.subscriptionWillFailForTerminatedAgent(validUtr, subscriptionRequestWithNoEdit(), arn = "TARN00023")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withCookies(Cookie("PLAY_LANG", "en"))
        sessionStoreService.currentSession.continueUrl = Some("/some/url")
        val result = await(controller.submitCheckAnswers(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.StartController.showCannotCreateAccount().url

        verifySubscriptionRequestSent(subscriptionRequestWithNoEdit())
        metricShouldExistAndBeUpdated("Count-Subscription-Failed-Agent_Terminated")
      }
    }
  }

  "GET /sign-in-with-new-user-id" should {
    "show the sign in with new user id error page" in new TestSetupNoJourneyRecord {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showSignInWithNewID(request))

      status(result) shouldBe 200
      checkHtmlResultWithBodyText(result, "Sign in with your new user ID",
        "You have not finished creating your agent services account.",
        "To finish this step, sign in again using the new user ID you created for your agent services account.",
      "If you do not have one")

      result should containLinkText("create a new user ID", routes.SignedOutController.redirectAgentToCreateCleanCreds().url)
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
      langForEmail = Some(Lang("en")),
      amlsDetails = Some(amlsDetails))


  protected def subscriptionRequestWithNoEdit() =
    SubscriptionRequest(
      utr = validUtr,
      knownFacts = SubscriptionRequestKnownFacts(validPostcode),
      agency = Agency(
        name = "My Trading Name",
        address = DesAddress(
          addressLine1 = testRegistration.address.addressLine1,
          addressLine2 = testRegistration.address.addressLine2,
          addressLine3 = testRegistration.address.addressLine3,
          addressLine4 = testRegistration.address.addressLine4,
          postcode = testRegistration.address.postalCode.get,
          countryCode = testRegistration.address.countryCode
        ),
        email = "email@email.com"
      ),
      langForEmail = Some(Lang("en")),
      amlsDetails = Some(amlsDetails)
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
      langForEmail = Some(Lang("en")),
      amlsDetails = Some(amlsDetails)
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

