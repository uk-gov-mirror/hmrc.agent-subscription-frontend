/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Logger
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

class ErrorHandlerSpec extends UnitSpec with GuiceOneServerPerSuite with LogCapturing {

  val handler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

  "ErrorHandler should show the error page" when {

    "a client error (400) occurs with log" in {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        val result = handler.onClientError(FakeRequest(), BAD_REQUEST, "some error")
        Thread.sleep(2000)
        status(result) shouldBe BAD_REQUEST
        logEvents.count(_.getMessage.contains(s"onClientError some error")) shouldBe 1
      }
    }

    "a client error (403) occurs" in {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        val result = handler.onClientError(FakeRequest(), FORBIDDEN, "some error")
        Thread.sleep(2000)
        status(result) shouldBe FORBIDDEN
        logEvents.count(_.getMessage.contains(s"global.error.403.message")) shouldBe 1
      }
    }

    "standardErrorTemplate shows up with log" in {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        handler.standardErrorTemplate("", "", "some error")(FakeRequest())
        logEvents.count(_.getMessage.contains(s"some error")) shouldBe 1
      }
    }
  }

}
