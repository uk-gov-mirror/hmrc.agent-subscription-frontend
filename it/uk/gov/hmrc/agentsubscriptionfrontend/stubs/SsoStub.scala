package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object SsoStub {

  def givenWhitelistedDomainsExist: StubMapping =
    stubFor(
      get(urlEqualTo("/sso/domains")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """{"externalDomains": ["127.0.0.1","online-qa.ibt.hmrc.gov.uk","ibt.hmrc.gov.uk"],"internalDomains":["localhost", "www.tax.service.gov.uk"]}""")))

  def givenWhitelistedDomainsError: StubMapping =
    stubFor(
      get(urlEqualTo("/sso/domains")).willReturn(
        aResponse()
          .withStatus(500)))
}
