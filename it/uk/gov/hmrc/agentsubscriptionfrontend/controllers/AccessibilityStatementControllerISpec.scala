package uk.gov.hmrc.agentsubscriptionfrontend.controllers

import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscriptionfrontend.support.BaseISpec

class AccessibilityStatementControllerISpec extends BaseISpec {

  private lazy val controller = app.injector.instanceOf[AccessibilityStatementController]

  "GET /accessibility-statement" should {
    "show the accessibility statement content" in {
      val result = await(controller.showAccessibilityStatement(FakeRequest()))

      status(result) shouldBe 200
      result should containMessages("accessibility.statement.h1")
     }
  }
}