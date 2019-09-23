package uk.gov.hmrc.agentsubscriptionfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault

object SsoStub {

  def givenDomainIsWhitelisted(domain: String) =
    stubFor(
      get(urlEqualTo(s"/sso/validate/domain/$domain"))
        .willReturn(aResponse()
          .withStatus(204)))

  def givenDomainIsNotWhitelisted(domain: String) =
    stubFor(
      get(urlEqualTo(s"/sso/validate/domain/$domain"))
        .willReturn(aResponse()
          .withStatus(400)))

  def givenDomainCheckFails(domain: String) =
    stubFor(
      get(urlEqualTo(s"/sso/validate/domain/$domain"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK)))
}
