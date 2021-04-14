package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import org.jsoup.Jsoup
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscriptionfrontend.config.AppConfig
import uk.gov.hmrc.agentsubscriptionfrontend.models.BusinessType.SoleTrader
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.BusinessDetails
import uk.gov.hmrc.agentsubscriptionfrontend.models._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AddressLookupFrontendStubs._
import uk.gov.hmrc.agentsubscriptionfrontend.stubs.AgentSubscriptionJourneyStub.{givenSubscriptionJourneyRecordExists, givenSubscriptionRecordCreated}
import uk.gov.hmrc.agentsubscriptionfrontend.support.SampleUser.subscribingAgentEnrolledForNonMTD
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestData.{businessAddress, registrationName, validPostcode, validUtr}
import uk.gov.hmrc.agentsubscriptionfrontend.support.{BaseISpec, TestData}
import uk.gov.hmrc.http.BadRequestException


class ContactDetailsControllerISpec extends BaseISpec {

  lazy val controller: ContactDetailsController = app.injector.instanceOf[ContactDetailsController]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val id = AuthProviderId("12345-credId")

  val returnFromAddressLookupUrl: String = routes.ContactDetailsController.returnFromAddressLookup().url

  "showContactEmailCheck (GET /contact-email-check) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with businessEmail in registration" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.p",
        "contactEmailCheck.option.yes",
        "contactEmailCheck.option.no",
        "contactEmailCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check-yes")
      val anotherEmailRadio = doc.getElementById("check-no")

      businessEmailRadio.hasAttr("checked") shouldBe false
      anotherEmailRadio.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with businessEmail in registration and contactEmail data exists" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            ),
            contactEmailData = Some(ContactEmailData(true, Some("test@gmail.com")))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      result should containMessages(
        "contactEmailCheck.title",
        "contactEmailCheck.p",
        "contactEmailCheck.option.yes",
        "contactEmailCheck.option.no",
        "contactEmailCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val businessEmailRadio = doc.getElementById("check-yes")
      val anotherEmailRadio = doc.getElementById("check-no")

      businessEmailRadio.hasAttr("checked") shouldBe true
      anotherEmailRadio.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no business email found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                None)
              )
            )))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showContactEmailCheck(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitContactEmailCheck (POST /contact-email-check) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

    "303 redirect to /task-list when same as business email selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "303 redirect to /contact-email-address when a different business email is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactEmailData = Some(ContactEmailData(false, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailAddress().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailCheck(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-email-check.invalid")
  }

  "throw a BadRequestException when invalid entry is submitted" in {

    givenSubscriptionJourneyRecordExists(id,
      TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          ))))

    val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

    intercept[BadRequestException] {
      await(controller.submitContactEmailCheck(request.withFormUrlEncodedBody("check" -> "INVALID")))
    }.getMessage should be("Strange form input value")
  }
}

  "showContactEmailAddress (GET /contact-email-address) " should {
  behave like anAgentAffinityGroupOnlyEndpoint (controller.showContactEmailCheck (_) )

  "200 OK with correct message content when subscriptionJourneyRecord exists and email-check visited" in {

    givenSubscriptionJourneyRecordExists(id,
      TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          )),
        contactEmailData = Some(ContactEmailData(true, None))))

    val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

    val result =
      await(controller.showContactEmailAddress(request))

    status(result) shouldBe 200

    result should containMessages("contactEmailAddress.title",
      "contactEmailAddress.p",
      "contactEmailAddress.button")

    result should containLink("button.back",routes.ContactDetailsController.showContactEmailCheck().url)
  }

    "303 Redirect to /contact-email-check when no contact email data found" in {

      givenSubscriptionJourneyRecordExists(id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
          businessDetails = BusinessDetails(SoleTrader,
            validUtr,
            Postcode(validPostcode),
            registration = Some(Registration(
              Some(registrationName),
              isSubscribedToAgentServices = false,
              isSubscribedToETMP = true,
              businessAddress,
              Some("email@email.com")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.showContactEmailAddress(request))

      status(result) shouldBe 303
     redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showContactEmailCheck().url)
    }
}

  "submitContactEmailAddress (POST /contact-email-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showContactEmailCheck(_))

  "303 redirect to /task-list when submit with valid email address" in {
    val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
      businessDetails = BusinessDetails(SoleTrader,
        validUtr,
        Postcode(validPostcode),
        registration = Some(Registration(
          Some(registrationName),
          isSubscribedToAgentServices = false,
          isSubscribedToETMP = true,
          businessAddress,
          Some("email@email.com")
        )
        )),
      contactEmailData = Some(ContactEmailData(true, None)))

    givenSubscriptionJourneyRecordExists(id, sjr)
    givenSubscriptionRecordCreated(id, sjr.copy(
      businessDetails = BusinessDetails(SoleTrader,
        validUtr,
        Postcode(validPostcode),
        registration = Some(Registration(
          Some(registrationName),
          isSubscribedToAgentServices = false,
          isSubscribedToETMP = true,
          businessAddress,
          Some("email@email.com")
        )
        )),
      contactEmailData = Some(ContactEmailData(true, Some("new@email.com"))))
    )
    val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

    val result =
      await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "new@email.com")))

    status(result) shouldBe 303
    redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
  }

    "200 OK with error message with empty submission" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          )),
        contactEmailData = Some(ContactEmailData(true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "")))

      status(result) shouldBe 200

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.empty")
    }

    "200 OK with error message with email that's too long submission" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          )),
        contactEmailData = Some(ContactEmailData(true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> TestData.emailTooLong)))

      status(result) shouldBe 200

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.maxLength")
    }

    "200 OK with error message with email that's invalid" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          )
          )),
        contactEmailData = Some(ContactEmailData(true, None)))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitContactEmailAddress(request.withFormUrlEncodedBody("email" -> "^$$%@email.$$*&com")))

      status(result) shouldBe 200

      result should containMessages("contactEmailAddress.title",
        "error.contact-email.invalidChar")
    }
  }

  "showTradingNameCheck (GET /trading-name) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showTradingNameCheck(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with taxpayerName in registration" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      result should containMessages(
       "contactTradingNameCheck.title",
        "contactTradingNameCheck.option.yes",
        "contactTradingNameCheck.option.no",
        "contactTradingNameCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check-yes")
      val tradingNameRadioNo = doc.getElementById("check-no")

      tradingNameRadioYes.hasAttr("checked") shouldBe false
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with taxpayerName in registration and contactTradingNameData exists" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            ),
            contactEmailData = Some(ContactEmailData(true, Some("test@gmail.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some(registrationName)))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      result should containMessages(
        "contactTradingNameCheck.title",
        "contactTradingNameCheck.option.yes",
        "contactTradingNameCheck.option.no",
        "contactTradingNameCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check-yes")
      val tradingNameRadioNo = doc.getElementById("check-no")

      tradingNameRadioYes.hasAttr("checked") shouldBe true
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no taxpayerName found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                None,
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                None)
              )
            )))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showTradingNameCheck(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitTradingNameCheck (POST /trading-name) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitTradingNameCheck(_))

    "303 redirect to /main-trading-name when No selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingNameData = Some(ContactTradingNameData(false, None)))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showTradingName().url)
    }

    "303 redirect to /task-list when Yes is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingNameData = Some(ContactTradingNameData(true, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitTradingNameCheck(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-trading-name-check.invalid")
    }

    "throw a BadRequestException when invalid entry is submitted" in {

      givenSubscriptionJourneyRecordExists(id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
          businessDetails = BusinessDetails(SoleTrader,
            validUtr,
            Postcode(validPostcode),
            registration = Some(Registration(
              Some(registrationName),
              isSubscribedToAgentServices = false,
              isSubscribedToETMP = true,
              businessAddress,
              Some("email@email.com")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      intercept[BadRequestException] {
        await(controller.submitTradingNameCheck(request.withFormUrlEncodedBody("check" -> "INVALID")))
      }.getMessage should be("Strange form input value")
    }
  }

  "showCheckMainTradingAddress (GET /check-trading-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showCheckMainTradingAddress(_))

    "200 OK with correct message content when subscriptionJourneyRecord exists with address in registration" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            )
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      result should containMessages(
        "contactTradingAddressCheck.title",
        "contactTradingAddressCheck.option.yes",
        "contactTradingAddressCheck.option.no",
        "contactTradingAddressCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check-yes")
      val tradingNameRadioNo = doc.getElementById("check-no")

      tradingNameRadioYes.hasAttr("checked") shouldBe false
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "200 OK with correct message content and radio button selected when subscriptionJourneyRecord exists " +
      "with address in registration and contactTradingAddressData exists" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = Some(Registration(
                Some(registrationName),
                isSubscribedToAgentServices = false,
                isSubscribedToETMP = true,
                businessAddress,
                Some("test@gmail.com")))
            ),
            contactEmailData = Some(ContactEmailData(true, Some("test@gmail.com"))),
            contactTradingNameData = Some(ContactTradingNameData(true, Some(registrationName))),
            contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress)))
          ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      result should containMessages(
        "contactTradingAddressCheck.title",
        "contactTradingAddressCheck.option.yes",
        "contactTradingAddressCheck.option.no",
        "contactTradingAddressCheck.continue.button"
      )

      val doc = Jsoup.parse(bodyOf(result))

      val tradingNameRadioYes = doc.getElementById("check-yes")
      val tradingNameRadioNo = doc.getElementById("check-no")

      tradingNameRadioYes.hasAttr("checked") shouldBe true
      tradingNameRadioNo.hasAttr("checked") shouldBe false
    }

    "303 Redirect to /start when no registration found in record" in {

      givenSubscriptionJourneyRecordExists(
        id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id)
          .copy(
            businessDetails = BusinessDetails(SoleTrader,
              validUtr,
              Postcode(validPostcode),
              registration = None
              )
            ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showCheckMainTradingAddress(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.StartController.start().url)
    }
  }

  "submitCheckMainTradingAddress (POST /check-main-trading-address) " should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.submitCheckMainTradingAddress(_))

    "303 redirect to /task-list when Yes selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com"))
          )
        ))

      givenSubscriptionJourneyRecordExists(id, sjr)

      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress))))
      )

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "yes")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }

    "303 redirect to /main-trading-address when No is selected" in {

      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)
      givenSubscriptionRecordCreated(id, sjr.copy(
        contactTradingAddressData = Some(ContactTradingAddressData(false, None))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "no")))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ContactDetailsController.showMainTradingAddress().url)
    }

    "200 OK with error messages when submit without making a choice" in {
      val sjr = TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        businessDetails = BusinessDetails(SoleTrader,
          validUtr,
          Postcode(validPostcode),
          registration = Some(Registration(
            Some(registrationName),
            isSubscribedToAgentServices = false,
            isSubscribedToETMP = true,
            businessAddress,
            Some("email@email.com")
          ))))

      givenSubscriptionJourneyRecordExists(id, sjr)

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.submitCheckMainTradingAddress(request))

      status(result) shouldBe 200

      result should containMessages("error.contact-trading-address-check.invalid")
    }

    "throw a BadRequestException when invalid entry is submitted" in {

      givenSubscriptionJourneyRecordExists(id,
        TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
          businessDetails = BusinessDetails(SoleTrader,
            validUtr,
            Postcode(validPostcode),
            registration = Some(Registration(
              Some(registrationName),
              isSubscribedToAgentServices = false,
              isSubscribedToETMP = true,
              businessAddress,
              Some("email@email.com")
            )
            ))))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      intercept[BadRequestException] {
        await(controller.submitCheckMainTradingAddress(request.withFormUrlEncodedBody("check" -> "INVALID")))
      }.getMessage should be("Strange form input value")
    }
  }

  "showMainTradingAddress (GET /find-main-trading-address)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.showMainTradingAddress(_))


    "303 redirect to specified location if init journey at address-lookup-frontend was successful" in {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAddressLookupInit( returnFromAddressLookupUrl)
      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)
      val result = await(controller.showMainTradingAddress(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(returnFromAddressLookupUrl)
    }
  }

  "returnFromAddressLookup (GET /lookup-trading-address)" should {
    behave like anAgentAffinityGroupOnlyEndpoint(controller.returnFromAddressLookup("")(_))

    "303 redirect to /task-list after successful address lookup" in {
      givenSubscriptionJourneyRecordExists(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id))
      givenAddressLookupReturnsAddress("address-id")
      givenSubscriptionRecordCreated(id, TestData.minimalSubscriptionJourneyRecordWithAmls(id).copy(
        contactTradingAddressData = Some(ContactTradingAddressData(true, Some(
         BusinessAddress("10 Other Place", Some("Some District"), Some("Line 3"), Some("Sometown"), Some("AA1 1AA"), "GB")
          )
        ))
      ))

      val request = authenticatedAs(subscribingAgentEnrolledForNonMTD)

      val result =
        await(controller.returnFromAddressLookup("address-id")(request))

      status(result) shouldBe 303

      redirectLocation(result) shouldBe Some(routes.TaskListController.showTaskList().url)
    }
  }


  }
