package uk.gov.hmrc.agentsubscriptionfrontend.controllers

class StartControllerWhitelistingISpec extends BaseControllerISpec {

  override protected def passcodeAuthenticationEnabled = true

  private lazy val controller: StartController = app.injector.instanceOf[StartController]

  "root" should {
    behave like aWhitelistedEndpoint(request => controller.root(request))
  }

  "start" should {
    behave like aWhitelistedEndpoint(request => controller.start(request))
  }
}
