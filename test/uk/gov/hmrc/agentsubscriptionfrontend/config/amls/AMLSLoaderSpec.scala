/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.config.amls

import uk.gov.hmrc.agentsubscriptionfrontend.config.amls.AMLSLoader.AMLSLoaderException
import uk.gov.hmrc.play.test.UnitSpec

class AMLSLoaderSpec extends UnitSpec {
  "AMLSLoaderSpec" should {
    "load all AMLS bodies from the csv file" in {
      val result = AMLSLoader.load("/amls.csv")

      result.size shouldBe 27
    }

    "each AMLS body should have a code a unique name" in {
      val result = AMLSLoader.load("/amls.csv")

      result("ACCA") shouldBe "Association of Chartered Certified Accountants (ACCA)"
      result("SRA") shouldBe "Solicitors Regulation Authority (SRA)"
    }

    "return exception if file contains an invalid line" in {
      val exception = intercept[AMLSLoaderException] {
        AMLSLoader.load("/invalid_amls.csv")
      }

      exception.getMessage should include("Strange line in AMLS csv file")
    }

    "return exception if file path is empty" in {
      val exception = intercept[AMLSLoaderException] {
        AMLSLoader.load("")
      }

      exception.getMessage should include("AMLS file path cannot be empty")
    }

    "return exception if file path is not csv" in {
      val exception = intercept[AMLSLoaderException] {
        AMLSLoader.load("/test.txt")
      }

      exception.getMessage should include("AMLS file should be a csv file")
    }
  }
}
