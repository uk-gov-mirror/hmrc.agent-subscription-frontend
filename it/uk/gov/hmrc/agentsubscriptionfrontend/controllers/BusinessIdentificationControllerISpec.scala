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

import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscriptionfrontend.audit.AgentSubscriptionFrontendEvent
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentAssuranceStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionStub._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AuthStub.userIsAuthenticated
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{BadRequestException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext.Implicits.global

trait BusinessIdentificationControllerISpec extends BaseISpec with SessionDataMissingSpec {
  val validBusinessTypes = Seq(
    IdentifyBusinessType.SoleTrader,
    IdentifyBusinessType.LimitedCompany,
    IdentifyBusinessType.Partnership,
    IdentifyBusinessType.Llp)
  val validUtr = Utr("2000000000")
  val validPostcode = "AA1 1AA"
  private val invalidPostcode = "11AAAA"
  private val blacklistedPostcode = "AB10 1ZT"

  val utr = Utr("0123456789")
  val postcode = "AA11AA"
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB")

  protected val initialDetails =
    InitialDetails(
      utr,
      "AA11AA",
      "My Agency",
      Some("agency@example.com"),
      businessAddress
    )

  def agentAssuranceRun: Boolean

  def agentAssurancePayeCheck: Boolean

  private lazy val redirectUrl: String = "http://localhost:9401/agent-services-account"

  private lazy val configuredGovernmentGatewayUrl = "http://configured-government-gateway.gov.uk/"

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .configure(
        "features.agent-assurance-run"        -> agentAssuranceRun,
        "features.agent-assurance-paye-check" -> agentAssurancePayeCheck,
        "government-gateway.url"              -> configuredGovernmentGatewayUrl
      )

  lazy val controller: BusinessIdentificationController = app.injector.instanceOf[BusinessIdentificationController]

  "showBusinessTypeForm (GET /business-type)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showBusinessTypeForm(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(
      controller.showBusinessTypeForm(_),
      sessionStoreService,
      userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))

    "contain page titles and header content" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))

      result should containMessages(
        "businessType.title",
        "businessType.progressive.title",
        "businessType.progressive.content.p1")
    }

    "contain radio options for Sole Trader, Limited Company, Partnership, and LLP" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))

      // Check form's radio inputs have correct values
      doc.getElementById("businessType-sole_trader").`val`() shouldBe "sole_trader"
      doc.getElementById("businessType-limited_company").`val`() shouldBe "limited_company"
      doc.getElementById("businessType-partnership").`val`() shouldBe "partnership"
      doc.getElementById("businessType-llp").`val`() shouldBe "llp"
    }

    "contain a link to sign out" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showBusinessTypeForm(request))
      val doc = Jsoup.parse(bodyOf(result))
      val signOutLink = doc.getElementById("sign-out")
      signOutLink.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutLink.text() shouldBe htmlEscapedMessage("businessType.progressive.content.link")
    }
  }

  "submitBusinessTypeForm (POST /business-type)" when {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitBusinessTypeForm(_))

    validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"redirect to /business-details when valid businessTypeIdentifier: $validBusinessTypeIdentifier" in {
        val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withFormUrlEncodedBody("businessType" -> validBusinessTypeIdentifier.key)

        val result = await(controller.submitBusinessTypeForm(request))
        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
          .submitBusinessDetailsForm(validBusinessTypeIdentifier)
          .url
      }
    }

    "choice is missing" should {
      "return 200 and redisplay the /business-type page with an error message for missing choice" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        val result = await(controller.submitBusinessTypeForm(request))
        result should containMessages("businessType.error.no-radio-selected")
      }
    }

    s"400 Exception ,when businessTypeIdentifier invalid" in {
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("businessType" -> "unCateredBusinessTypeIdentifier")

      an[BadRequestException] shouldBe thrownBy(await(controller.submitBusinessTypeForm(request)))
    }
  }

  "redirectToBusinessType" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.redirectToBusinessType(_))

    behave like aPageTakingContinueUrlAndCachingInSessionStore(
      controller.redirectToBusinessType(_),
      sessionStoreService,
      userIsAuthenticated(subscribingCleanAgentWithoutEnrolments),
      expectedStatusCode = 303)

    "redirect to /business-type" in {
      val result = await(controller.redirectToBusinessType(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showBusinessTypeForm().url)
    }
  }

  "showBusinessDetailsForm" should {
    val playRequestValidBusinessTypeIdentifier =
      controller.showBusinessDetailsForm(validBusinessTypes.head)

    behave like anAgentAffinityGroupOnlyEndpoint(playRequestValidBusinessTypeIdentifier(_))

    behave like aPageWithFeedbackLinks(
      playRequestValidBusinessTypeIdentifier(_),
      authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "caches continue URL parameter" when {
      "valid businessType parameter was supplied" should {
        behave like aPageTakingContinueUrlAndCachingInSessionStore(
          playRequestValidBusinessTypeIdentifier(_),
          sessionStoreService,
          userIsAuthenticated(subscribingCleanAgentWithoutEnrolments))
      }
    }

    "display the check agency status page if the current user is logged in and has affinity group = Agent" in {
      val result =
        await(playRequestValidBusinessTypeIdentifier(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("businessDetails.title")
      metricShouldExistAndBeUpdated("Count-Subscription-BusinessDetails-Start")
    }

    "display the AS Account Page if the current user has HMRC-AS-AGENT enrolment" in {
      val result =
        await(playRequestValidBusinessTypeIdentifier(authenticatedAs(subscribingAgentEnrolledForHMRCASAGENT)))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(redirectUrl)
      metricShouldExistAndBeUpdated("Count-Subscription-AlreadySubscribed-HasEnrolment-AgentServicesAccount")
    }

    validBusinessTypes.foreach { validBusinessTypeIdentifier =>
      s"show check Agency Status page for valid businessTypeIdentifier: $validBusinessTypeIdentifier" in {
        val result = await(
          controller.showBusinessDetailsForm(validBusinessTypeIdentifier)(
            authenticatedAs(subscribingCleanAgentWithoutEnrolments)))
        status(result) shouldBe 200

        containSubstrings(routes.BusinessIdentificationController.showBusinessTypeForm().url)
        containMessages("back.button", "businessDetails.title", s"businessDetails.label.utr.${validBusinessTypeIdentifier.key}")
      }
    }
  }

  "submitBusinessDetailsForm" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request =>
      controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

    "return a 200 response to redisplay the form with an error message for invalidly-formatted UTR" in {
      val invalidUtr = "0123456"
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe OK
      containMessages("businessDetails.title", "error.sautr.invalid")
      containSubstrings(invalidUtr, validPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 303 redirect to no-match page for UTR failing to pass Modulus11Check" in {
      val invalidUtr = "2000000001" // Modulus11Check validation fails in this case
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> invalidUtr, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoAgencyFound().url)
      metricShouldExistAndBeUpdated("Count-Subscription-NoAgencyFound")
    }

    "return a 200 response to redisplay the form with an error message for invalidly-formatted postcode" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> invalidPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include("Enter a valid postcode, for example AA1 1AA")
      responseBody should include(validUtr.value)
      responseBody should include(invalidPostcode)
      noMetricExpectedAtThisPoint()
    }

    "return a 200 response to redisplay the form with an error message for empty form parameters" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe OK
      val responseBody = bodyOf(result)
      responseBody should include(htmlEscapedMessage("businessDetails.title"))
      responseBody should include(htmlEscapedMessage("error.sautr.blank"))
      responseBody should include("Enter a postcode")
      noMetricExpectedAtThisPoint()
    }

    "redirect to no-match page when no matching registration found by agent-subscription" in {
      withNonMatchingUtrAndPostcode(validUtr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showNoAgencyFound().url)
      metricShouldExistAndBeUpdated("Count-Subscription-NoAgencyFound")
    }

    "propagate an exception when there is no organisation name" in {
      withNoOrganisationName(validUtr, validPostcode)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val e = intercept[IllegalStateException] {
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))
      }
      e.getMessage should include(validUtr.value)
    }

    "showAlreadySubscribed, when fully subscribed" in {
      withMatchingUtrAndPostcode(validUtr, validPostcode, isSubscribedToAgentServices = true, isSubscribedToETMP = true)
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showAlreadySubscribed().url)
    }

    "showSubscriptionComplete for partially subscribed agent" in {
      withMatchingUtrAndPostcode(
        validUtr,
        validPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)),
        arn = "TARN00023")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))
      redirectLocation(result) shouldBe Some(routes.SubscriptionController.showSubscriptionComplete().url)
    }

    "showCreateNewAccount, creds with enrolment/s are not allowed when partiallySubscribed User" in {
      withMatchingUtrAndPostcode(
        validUtr,
        validPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillSucceed(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)))

      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      val result =
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request))
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showCreateNewAccount().url)

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
      import scala.concurrent.ExecutionContext.Implicits.global

      await(sessionStoreService.fetchKnownFactsResult) shouldBe Some(
        KnownFactsResult(Utr("2000000000"), "AA1 1AA", "My Agency", isSubscribedToAgentServices = false, None, None))
      await(sessionStoreService.fetchInitialDetails) shouldBe None
    }

    "Upstream4xxResponse partialSubscriptionFix failed with 403" in {
      withMatchingUtrAndPostcode(
        validUtr,
        validPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)),
        403)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream4xxResponse] shouldBe thrownBy(
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request)))
    }

    "Upstream4xxResponse partialSubscriptionFix failed with 409 as someone has already been allocated the enrolment" in {
      withMatchingUtrAndPostcode(
        validUtr,
        validPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)),
        409)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream4xxResponse] shouldBe thrownBy(
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request)))
    }

    "Upstream5xxResponse partialSubscriptionFix failed for partiallySubscribed User" in {
      withMatchingUtrAndPostcode(
        validUtr,
        validPostcode,
        isSubscribedToAgentServices = false,
        isSubscribedToETMP = true)
      AgentSubscriptionStub.partialSubscriptionWillReturnStatus(
        CompletePartialSubscriptionBody(validUtr, knownFacts = SubscriptionRequestKnownFacts(validPostcode)),
        500)

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withFormUrlEncodedBody("utr" -> validUtr.value, "postcode" -> validPostcode)
      an[Upstream5xxResponse] shouldBe thrownBy(
        await(controller.submitBusinessDetailsForm(validBusinessTypes.head)(request)))
    }
  }

  "showCreateNewAccount" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showCreateNewAccount(request))
    behave like aPageWithFeedbackLinks(
      controller.showCreateNewAccount(_),
      authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the has other enrolments page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showCreateNewAccount(authenticatedAs(subscribingAgentEnrolledForNonMTD)))

      result should containMessages("createNewAccount.title")
    }
  }

  "showNoAgencyFound" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showNoAgencyFound(request))
    behave like aPageWithFeedbackLinks(request => {
      controller.showNoAgencyFound(request)
    }, authenticatedAs(subscribingCleanAgentWithoutEnrolments))

    "display the no agency found page if the current user is logged in and has affinity group = Agent" in {
      val result = await(controller.showNoAgencyFound(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("noAgencyFound.title")
    }
  }

  "showConfirmBusinessForm" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showConfirmBusinessForm(request))

    "display the confirm business page if the current user is logged in and has affinity group = Agent" in {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = utr,
          postcode = postcode,
          taxpayerName = registrationName,
          isSubscribedToAgentServices = false,
          Some(businessAddress),
          Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containMessages(
        "confirmBusiness.title",
        "button.back",
        "confirmBusiness.option.yes",
        "confirmBusiness.option.no")

      result should containSubstrings(
        s"$postcode",
        "01234 56789",
        s"$registrationName",
        s"${businessAddress.addressLine1}",
        s"${businessAddress.addressLine2.get}",
        s"${businessAddress.addressLine3.get}",
        s"${businessAddress.addressLine4.get}"
      )
    }

    "show utr in the correct format" in {
      val utr = Utr("0123456789")
      val postcode = "AA11AA"
      val registrationName = "My Agency"

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        .withSession("businessType" -> "sole_trader")
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = utr,
          postcode = postcode,
          taxpayerName = registrationName,
          isSubscribedToAgentServices = false,
          Some(businessAddress),
          Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings("01234 56789")
    }

    "redirect to GET /business-type when no businessType in session" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      redirectLocation(await(controller.showConfirmBusinessForm(request)))
        .get shouldBe routes.BusinessIdentificationController.showBusinessTypeForm().url
    }

    "show a back button which allows the user to return to the business-details page" in {
      implicit val request =
        authenticatedAs(subscribingAgentEnrolledForNonMTD).withSession("businessType" -> "sole_trader")
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = Utr("0123456789"),
          postcode = "AA11AA",
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress),
          Some("someone@example.com")))

      val result = await(controller.showConfirmBusinessForm(request))

      result should containSubstrings(
        routes.BusinessIdentificationController.submitBusinessDetailsForm(IdentifyBusinessType.SoleTrader).url)
    }

    "redirect to the Check Business Type  page if there is no KnownFactsResult in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result = await(controller.showConfirmBusinessForm(request))

      resultShouldBeSessionDataMissing(result)
      noMetricExpectedAtThisPoint()
    }
  }

  "submitConfirmBusiness" when {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitConfirmBusinessForm(request))

    "User chooses Yes" should {
      "redirect to showAlreadySubscribed if the user is already subscribed and isSubscribedToAgentServices=true" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = true,
            Some(businessAddress),
            Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showAlreadySubscribed().url
        metricShouldExistAndBeUpdated("Count-Subscription-AlreadySubscribed-RegisteredInETMP")
      }

      "redirect to showMoneyLaunderingComplianceForm if the user has clean creds and isSubscribedToAgentServices=false" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.AMLSController.showMoneyLaunderingComplianceForm().url
      }

      "redirect to showBusinessEmailForm if the user has clean creds and isSubscribedToAgentServices=false and ETMP record contains empty email" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            None))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showBusinessEmailForm().url
      }

      "redirect to showBusinessNameForm if the user has clean creds and isSubscribedToAgentServices=false and ETMP record contains invalid name" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency &",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            None))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showBusinessNameForm().url
      }

      "redirect to showBusinessNameForm if the user has clean creds and isSubscribedToAgentServices=false and " +
        "ETMP record contains invalid name and invalid address" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency &",
            isSubscribedToAgentServices = false,
            Some(businessAddress.copy(addressLine1 = "invalid address *")),
            None
          ))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController.showBusinessNameForm().url
      }

      "redirect to showUpdateBusinessAddressForm if the user has clean creds and isSubscribedToAgentServices=false " +
        "and ETMP record contains invalid address" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "yes")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress.copy(addressLine1 = "invalid address *")),
            None
          ))

        val result = await(controller.submitConfirmBusinessForm(request))

        sessionStoreService.currentSession.initialDetails should not be empty

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
          .showUpdateBusinessAddressForm()
          .url
      }

      "redirect to showUpdateBusinessAddressForm if the user has clean creds and isSubscribedToAgentServices=false and" when {
        "ETMP record contains blacklisted postcode" in {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
            .withSession("businessType" -> "sole_trader")
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.knownFactsResult = Some(
            KnownFactsResult(
              utr = Utr("0123456789"),
              postcode = "AA11AA",
              taxpayerName = "My Agency",
              isSubscribedToAgentServices = false,
              Some(businessAddress.copy(postalCode = Some(blacklistedPostcode))),
              None
            ))

          val result = await(controller.submitConfirmBusinessForm(request))

          sessionStoreService.currentSession.initialDetails should not be empty

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }

        "ETMP record contains BFPO postcode starting with BF" in {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
            .withSession("businessType" -> "sole_trader")
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.knownFactsResult = Some(
            KnownFactsResult(
              utr = Utr("0123456789"),
              postcode = "AA11AA",
              taxpayerName = "My Agency",
              isSubscribedToAgentServices = false,
              Some(businessAddress.copy(postalCode = Some("BF1 1XX"))),
              None
            ))

          val result = await(controller.submitConfirmBusinessForm(request))

          sessionStoreService.currentSession.initialDetails should not be empty

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }

        "ETMP record contains BFPO postcode starting with BFPO" in {
          implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
            .withSession("businessType" -> "sole_trader")
            .withFormUrlEncodedBody("confirmBusiness" -> "yes")
          sessionStoreService.currentSession.knownFactsResult = Some(
            KnownFactsResult(
              utr = Utr("0123456789"),
              postcode = "AA11AA",
              taxpayerName = "My Agency",
              isSubscribedToAgentServices = false,
              Some(businessAddress.copy(postalCode = Some("BFPO15"))),
              None
            ))

          val result = await(controller.submitConfirmBusinessForm(request))

          sessionStoreService.currentSession.initialDetails should not be empty

          result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
            .showUpdateBusinessAddressForm()
            .url
        }
      }
    }

    "User chooses No" should {
      "redirect to the business-details page" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "no")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
          .showBusinessDetailsForm(validBusinessTypes.head)
          .url
      }

      "redirect to show business-type page when no reference to business type of subscription" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withFormUrlEncodedBody("confirmBusiness" -> "no")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result.header.headers(LOCATION) shouldBe routes.BusinessIdentificationController
          .showBusinessTypeForm()
          .url
      }
    }

    "choice is missing" should {
      "return 200 and redisplay the /confirm-business page with an error message for missing choice" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "")

        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            Some("someone@example.com")))

        val result = await(controller.submitConfirmBusinessForm(request))

        result should containMessages("confirmBusiness.title", "confirmBusiness.error.no-radio-selected")
      }
    }

    "form value is invalid" should {
      "result in a BadRequest" in {
        implicit val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
          .withSession("businessType" -> "sole_trader")
          .withFormUrlEncodedBody("confirmBusiness" -> "INVALID")
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = Utr("0123456789"),
            postcode = "AA11AA",
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress),
            Some("someone@example.com")))

        a[BadRequestException] shouldBe thrownBy(await(controller.submitConfirmBusinessForm(request)))
      }
    }
  }

  "showBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display business name form if the name is des complaint" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.showBusinessNameForm(request))
      result should containMessages("businessName.title", "businessName.description", "businessName.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("name").`val` shouldBe initialDetails.name

      val backLink = doc.getElementsByClass("link-back")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "display business name form if the name is not des complaint" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails.copy(name = "My Agency &"))

      val result = await(controller.showBusinessNameForm(request))
      result should containMessages(
        "businessName.updated.title",
        "businessName.updated.p1",
        "businessName.description",
        "businessName.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("name").`val` shouldBe "My Agency &"

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessNameForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitBusinessNameForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business name after submission" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "new Agent name")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitBusinessNameForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.AMLSController.showMoneyLaunderingComplianceForm().url

      await(sessionStoreService.fetchInitialDetails).get.name shouldBe "new Agent name"
    }

    "show validation error when the form is submitted with empty name" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.empty")
    }

    "show validation error when the form is submitted with non des complaint name after check-answers page" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.title", "error.business-name.invalid")
    }

    "show validation error when the form is submitted with non des complaint name" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("name" -> "Some name *")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails.copy(name = "Some name &"))

      val result = await(controller.submitBusinessNameForm(request))

      result should containMessages("businessName.updated.title", "error.business-name.invalid")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.submitBusinessNameForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessEmailForm(request))

    "display business email form" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "businessEmail.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe initialDetails.email.get

      val backLink = doc.getElementsByClass("link-back")
      backLink.attr("href") shouldBe routes.SubscriptionController.showCheckAnswers().url

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "display business email form when email address in initial details is empty" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails = Some(initialDetails.copy(email = None))

      val result = await(controller.showBusinessEmailForm(request))
      result should containMessages("businessEmail.title", "businessEmail.description", "businessEmail.continue.button")
      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("email").`val` shouldBe ""

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitBusinessEmailForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showBusinessNameForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitBusinessEmailForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessEmailForm(request))

    "update business email after submission" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
        "email" -> "newagent@example.com")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitBusinessEmailForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.AMLSController.showMoneyLaunderingComplianceForm().url

      await(sessionStoreService.fetchInitialDetails).get.email shouldBe Some("newagent@example.com")
    }

    "show validation error when the form is submitted with empty email" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("email" -> "")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitBusinessEmailForm(request))

      result should containMessages("businessEmail.title", "error.business-email.empty")
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.submitBusinessEmailForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showBusinessNameForm(request))

    "display update business address form if the address is not des complaint" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.initialDetails =
        Some(initialDetails.copy(businessAddress = businessAddress.copy(addressLine1 = "Address line 1 &")))

      val result = await(controller.showUpdateBusinessAddressForm(request))
      result should containMessages(
        "updateBusinessAddress.title",
        "updateBusinessAddress.p1",
        "updateBusinessAddress.p2",
        "updateBusinessAddress.address_line_1.title",
        "updateBusinessAddress.address_line_2.title",
        "updateBusinessAddress.address_line_3.title",
        "updateBusinessAddress.address_line_4.title",
        "updateBusinessAddress.postcode.title",
        "updateBusinessAddress.continue"
      )

      val doc = Jsoup.parse(bodyOf(result))
      doc.getElementById("addressLine1").`val` shouldBe "Address line 1 &"
      doc.getElementById("addressLine2").`val` shouldBe businessAddress.addressLine2.get
      doc.getElementById("addressLine3").`val` shouldBe businessAddress.addressLine3.get
      doc.getElementById("addressLine4").`val` shouldBe businessAddress.addressLine4.get
      doc.getElementById("postcode").`val` shouldBe businessAddress.postalCode.get

      val form = doc.select("form").first()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.BusinessIdentificationController.submitUpdateBusinessAddressForm().url
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.showUpdateBusinessAddressForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "submitUpdateBusinessAddressForm" should {
    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.submitBusinessNameForm(request))

    "update business address after submission" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "new addressline 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitUpdateBusinessAddressForm(request))
      status(result) shouldBe 303
      redirectLocation(result).head shouldBe routes.AMLSController.showMoneyLaunderingComplianceForm().url

      val updatedBusinessAddress = await(sessionStoreService.fetchInitialDetails).get.businessAddress

      updatedBusinessAddress.addressLine1 shouldBe "new addressline 1"
      updatedBusinessAddress.addressLine2 shouldBe Some("new addressline 2")
      updatedBusinessAddress.addressLine3 shouldBe Some("new addressline 3")
      updatedBusinessAddress.addressLine4 shouldBe Some("new addressline 4")
      updatedBusinessAddress.postalCode shouldBe Some("BB11BB")
    }

    "show validation error when the form is submitted with empty address line 1" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> " ",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB")
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.empty")
    }

    "show validation error when the form is submitted with invalid address line 3" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline **!",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_3.title", "error.addressline.3.invalid")
    }

    "show validation error when the form is submitted with invalid address line 1" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1**",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BB"
        )
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.address_line_1.title", "error.addressline.1.invalid")
    }

    "show validation error when the form is submitted with postcode which exceed max length" in {
      implicit val request =
        authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody(
          "addressLine1" -> "address line 1",
          "addressLine2" -> "new addressline 2",
          "addressLine3" -> "new addressline 3",
          "addressLine4" -> "new addressline 4",
          "postcode"     -> "BB11BBBBBBBBBBBBBBB"
        )
      sessionStoreService.currentSession.initialDetails = Some(initialDetails)

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      result should containMessages("updateBusinessAddress.postcode.title", "error.postcode.maxlength")
    }

    "redirect to postcode-not-allowed page" when {
      "postcode entered is blacklisted" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode" -> blacklistedPostcode)
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode entered is BFPO" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode" -> "BF11XX")
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }

      "postcode starts with BFPO" in {
        implicit val request =
          authenticatedAs(subscribingCleanAgentWithoutEnrolments).withFormUrlEncodedBody("addressLine1" -> "address line 1",
            "addressLine2" -> "new addressline 2",
            "addressLine3" -> "new addressline 3",
            "addressLine4" -> "new addressline 4",
            "postcode" -> "BFPO15")
        sessionStoreService.currentSession.initialDetails = Some(initialDetails)

        val result = await(controller.submitUpdateBusinessAddressForm(request))
        status(result) shouldBe 303
        redirectLocation(result).head shouldBe routes.BusinessIdentificationController.showPostcodeNotAllowed().url
      }
    }

    "redirect to the /business-type page if there is no InitialDetails in session because the user has returned to a bookmark" in {
      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)

      val result = await(controller.submitUpdateBusinessAddressForm(request))

      resultShouldBeSessionDataMissing(result)
    }
  }

  "showAlreadySubscribed" should {

    behave like anAgentAffinityGroupOnlyEndpoint(request => controller.showAlreadySubscribed(request))

    "display the already subscribed page if the current user is logged in and has affinity group = Agent" in {

      val result = await(controller.showAlreadySubscribed(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))

      result should containMessages("alreadySubscribed.title")
      val doc = Jsoup.parse(bodyOf(result))
      val signOutButton = doc.getElementById("finishSignOut")
      signOutButton.attr("href") shouldBe routes.SignedOutController.signOutWithContinueUrl.url
      signOutButton.text() shouldBe htmlEscapedMessage("button.finishSignOut")

    }
  }

  "invasive check" should {
    "return 200 and redisplay the invasiveSaAgentCodePost page with an error message for missing radio choice" in {
      val result = await(controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)))
      Messages("invasive.error.no-radio.selected").r.findAllMatchIn(bodyOf(result)).size shouldBe 2
    }

    "start invasiveCheck if selected Yes with SaAgentCode" when {

      "input contains only capital letters" in { testSaAgentCodeCheck("SA6012") }
      "input contains letters in mixed case" in { testSaAgentCodeCheck("sA6012") }
      "input contains letters in lower case" in { testSaAgentCodeCheck("sa6012") }

      def testSaAgentCodeCheck(sageAgentCode: String) = {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", sageAgentCode))))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showClientDetailsForm().url)
        noMetricExpectedAtThisPoint()
      }
    }

    "redirect to /cannot-create account if selected No" in {

      val result = await(
        controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("hasSaAgentCode", "false"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Declined")
    }

    "return 200 and display page with error when failing the validation of SaAgentCode" when {
      "it contains invalid characters" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA601*2AAAA"))))

        result should containMessages("error.saAgentCode.invalid")
        result should repeatMessage("error.saAgentCode.invalid", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong max length" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA6012AAAA"))))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "it contains wrong min length" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", "SA"))))

        result should containMessages("error.saAgentCode.length")
        result should repeatMessage("error.saAgentCode.length", 2)
        noMetricExpectedAtThisPoint()
      }

      "contains empty SaAgentCode" in {
        val result = await(
          controller.invasiveSaAgentCodePost(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("hasSaAgentCode", "true"), ("saAgentCode", ""))))

        result should containMessages("error.saAgentCode.blank")
        result should repeatMessage("error.saAgentCode.blank", 2)
        noMetricExpectedAtThisPoint()
      }
    }

    "redirect to confirm business when successfully submitting nino" when {

      "input nino contains only capital letters" in { testInvasiveCheckWithNino("AA123456A") }
      "input nino contains mixed case letters" in { testInvasiveCheckWithNino("Aa123456a") }
      "input nino contains only lowercase letters" in { testInvasiveCheckWithNino("aa123456a") }
      "input nino contains random spaces" in { testInvasiveCheckWithNino("AA1   2 3 4 5 6        A ") }

      def testInvasiveCheckWithNino(nino: String) = {
        givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = validUtr,
            postcode = validPostcode,
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            Some(businessAddress), Some("someone@example.com")))

        val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "nino"), ("nino", nino))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

        verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), true, "SA6012", agentAssurancePayeCheck)
        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
      }
    }

    "redirect to invasive check start when no SACode in session to obtain it again" in {
      givenNinoAGoodCombinationAndUserHasRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(controller.submitClientDetailsForm(request
        .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.invasiveCheckStart().url)
    }

    "redirect to /cannot-create account page when submitting valid nino with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("nino", "AA123456A", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false, None, None))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123456A"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Nino("AA123456A"), false, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "clientDetails no variant selected" in {
      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", ""))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("clientDetails.error.no-radio.selected"))
    }

    "nino invalid send back 200 with error page" in {
      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", "AA123"))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.clientdetails.nino.invalid"))
    }

    "nino empty send back 200 with error page" in {

      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "nino"), ("nino", ""))))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.clientdetails.nino.empty"))
    }

    "redirect to confirm business when successfully submitting UTR" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to confirm business when successfully submitting UTR with random spaces" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
          Some(businessAddress), Some("someone@example.com")))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "   40000      00     009  "))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.BusinessIdentificationController.showConfirmBusinessForm().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(Utr("4000000009"), true, "SA6012", agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Success")
    }

    "redirect to /cannot-create account page" when {
      "submitting invalid Utr which fails Modulus11Check" in {
        implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
        sessionStoreService.currentSession.knownFactsResult = Some(
          KnownFactsResult(
            utr = validUtr,
            postcode = validPostcode,
            taxpayerName = "My Agency",
            isSubscribedToAgentServices = false,
            None,
            None))

        val result = await(
          controller.submitClientDetailsForm(
            request
              .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000019"))
              .withSession("saAgentReferenceToCheck" -> "SA6012")))

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

        metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
    }

    "submitting valid utr with no relationship" in {
      givenAUserDoesNotHaveRelationshipInCesa("utr", "40000     00  009", "SA6012")

      implicit val request = authenticatedAs(subscribingCleanAgentWithoutEnrolments)
      sessionStoreService.currentSession.knownFactsResult = Some(
        KnownFactsResult(
          utr = validUtr,
          postcode = validPostcode,
          taxpayerName = "My Agency",
          isSubscribedToAgentServices = false,
            None,
            None))

      val result = await(
        controller.submitClientDetailsForm(
          request
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
          Utr("4000000009"),
          false,
          "SA6012",
          agentAssurancePayeCheck)
      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Failed")
    }

    "successfully selecting ICannotProvideEitherOfTheseDetails" in {
      givenUtrAGoodCombinationAndUserHasRelationshipInCesa("utr", "4000000009", "SA6012")

      val result = await(
        controller.submitClientDetailsForm(authenticatedAs(subscribingCleanAgentWithoutEnrolments)
          .withFormUrlEncodedBody(("variant", "cannotProvide"))
          .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.showCannotCreateAccount().url)

      metricShouldExistAndBeUpdated("Count-Subscription-InvasiveCheck-Could-Not-Provide-Tax-Payer-Identifier")
    }
  }

    "utr blank invasiveCheck" in {

      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", ""))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.blank"))
    }

    "utr invalid send back 200 with error page" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "4ABC000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "utr wrong length" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "utr"), ("utr", "40000000090000000"))
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
      bodyOf(result) should include(htmlEscapedMessage("error.client.sautr.invalid"))
    }

    "Invalid form 'variant' cannot determine which option user selected  " in {
      an[BadRequestException] shouldBe thrownBy(await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody(("variant", "someInvalidVariant"), ("utr", "4000000009"))
            .withSession("saAgentReferenceToCheck" -> "SA6012"))))
    }

    "return 200 error when submitting without selected radio option" in {
      val result = await(
        controller.submitClientDetailsForm(
          authenticatedAs(subscribingCleanAgentWithoutEnrolments)
            .withFormUrlEncodedBody()
            .withSession("saAgentReferenceToCheck" -> "SA6012")))

      status(result) shouldBe 200
    }
  }

  def verifyAgentAssuranceAuditRequestSent(
                                            passPayeAgentAssuranceCheck: Option[Boolean],
                                            passSaAgentAssuranceCheck: Option[Boolean],
                                            passVatDecOrgAgentAssuranceCheck: Option[Boolean],
                                            passIRCTAgentAssuranceCheck: Option[Boolean]): Unit = {
    val optional = Seq(
      passPayeAgentAssuranceCheck.map("passPayeAgentAssuranceCheck" -> _.toString),
      passSaAgentAssuranceCheck.map("passSaAgentAssuranceCheck" -> _.toString),
      passVatDecOrgAgentAssuranceCheck.map("passVatDecOrgAgentAssuranceCheck" -> _.toString),
      passIRCTAgentAssuranceCheck.map("passIRCTAgentAssuranceCheck" -> _.toString)).flatten

    verifyAuditRequestSent(
      1,
      AgentSubscriptionFrontendEvent.AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "true",
        "saAgentRef" -> "FOO1234",
        //TODO "refuseToDealWith" -> ?,
        "isEnrolledPAYEAgent" -> "true",
        "payeAgentRef" -> "HZ1234",
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway"
      ) ++ optional,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }

  def verifyAgentAssuranceAuditRequestSentWithClientIdentifier(
                                                                identifier: TaxIdentifier,
                                                                passCESAAgentAssuranceCheck: Boolean,
                                                                saAgentRef: String,
                                                                aAssurancePayeCheck: Boolean): Unit = {

    val clientIdentifier = identifier match {
      case nino@Nino(_) => ("userEnteredNino" -> nino.value)
      case utr@Utr(_) => ("userEnteredUtr" -> utr.value)
    }
    val payeAudit = if (aAssurancePayeCheck) Seq("passPayeAgentAssuranceCheck" -> "false") else Seq.empty

    verifyAuditRequestSent(
      1,
      AgentSubscriptionFrontendEvent.AgentAssurance,
      detail = Map(
        "utr" -> validUtr.value,
        "postcode" -> validPostcode,
        "isEnrolledSAAgent" -> "false",
        "passSaAgentAssuranceCheck" -> "false",
        "isEnrolledPAYEAgent" -> "false",
        "passCESAAgentAssuranceCheck" -> passCESAAgentAssuranceCheck.toString,
        "authProviderId" -> "12345-credId",
        "authProviderType" -> "GovernmentGateway",
        "userEnteredSaAgentRef" -> saAgentRef
      ) + clientIdentifier ++ payeAudit,
      tags = Map("transactionName" -> "agent-assurance", "path" -> "/")
    )
  }
}
