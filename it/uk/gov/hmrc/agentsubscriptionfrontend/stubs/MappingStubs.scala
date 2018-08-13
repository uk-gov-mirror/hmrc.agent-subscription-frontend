package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}

object MappingStubs {
  private val urlEligibility = "/agent-mapping/mappings/eligibility"
  private def urlPreSubscription(utr: String) = s"/agent-mapping/mappings/pre-subscription/utr/$utr"
  private def urlUpdateToPostSubscription(utr: String) = s"/agent-mapping/mappings/post-subscription/utr/$utr"

  def givenMappingEligibilityIsEligible: StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(ok("""{ "hasEligibleEnrolments" : true }""")))

  def givenMappingEligibilityIsNotEligible: StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(ok("""{ "hasEligibleEnrolments" : false }""")))

  def givenMappingEligibilityCheckFails(httpReturnCode: Int): StubMapping =
    stubFor(
      get(urlEqualTo(urlEligibility))
        .willReturn(status(httpReturnCode)))

  def verifyMappingEligibilityCalled(times: Int = 1) =
    verify(times, getRequestedFor(urlEqualTo(urlEligibility)))

  def givenMappingCreatePreSubscription(utr: Utr, httpReturnCode: Int = 201): StubMapping =
    stubFor(
      put(urlEqualTo(urlPreSubscription(utr.value)))
        .willReturn(status(httpReturnCode)))

  def verifyMappingCreatePreSubscriptionCalled(utr: Utr, times: Int = 1) =
    verify(times, putRequestedFor(urlEqualTo(urlPreSubscription(utr.value))))

  def givenMappingDeletePreSubscription(utr: Utr, httpReturnCode: Int = 204): StubMapping =
    stubFor(
      delete(urlEqualTo(urlPreSubscription(utr.value)))
        .willReturn(status(httpReturnCode)))

  def verifyMappingDeletePreSubscriptionCalled(utr: Utr, times: Int = 1) =
    verify(times, deleteRequestedFor(urlEqualTo(urlPreSubscription(utr.value))))

  def givenMappingUpdateToPostSubscription(utr: Utr, httpReturnCode: Int = 200): StubMapping =
    stubFor(
      put(urlEqualTo(urlUpdateToPostSubscription(utr.value)))
        .willReturn(status(httpReturnCode)))

  def verifyMappingUpdateToPostSubscriptionCalled(utr: Utr, times: Int = 1) =
    verify(times, putRequestedFor(urlEqualTo(urlUpdateToPostSubscription(utr.value))))

}
