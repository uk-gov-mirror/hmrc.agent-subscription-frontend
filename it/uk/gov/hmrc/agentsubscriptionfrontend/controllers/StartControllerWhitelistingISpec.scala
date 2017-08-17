package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec

class StartControllerWhitelistingISpec extends BaseISpec {

  override protected def passcodeAuthenticationEnabled = true

  private lazy val controller: StartController = app.injector.instanceOf[StartController]

  "root" should {
    behave like aWhitelistedEndpoint(request => controller.root(request))
  }

  "start" should {
    behave like aWhitelistedEndpoint(request => controller.start()(request))
  }
}
