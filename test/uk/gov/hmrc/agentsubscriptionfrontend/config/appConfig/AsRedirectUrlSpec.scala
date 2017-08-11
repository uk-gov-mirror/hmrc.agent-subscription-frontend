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

package uk.gov.hmrc.agentsubscriptionfrontend.config.appConfig

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.agentsubscriptionfrontend.support.TestAppConfig
import uk.gov.hmrc.play.test.UnitSpec

class AsRedirectUrlSpec extends UnitSpec with GuiceOneAppPerSuite{

  private lazy val ASAccountUrl = "localhost:9401/agent-services-account"

  "AS redirect url" should {
    "successfully being concatenated from 2 separate configs" in{
      TestAppConfig.redirectUrl shouldEqual ASAccountUrl
    }
  }
}
