/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes

import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes.PostcodesLoader.PostcodeLoaderException
import uk.gov.hmrc.play.test.UnitSpec

class PostcodesLoaderSpec extends UnitSpec with MockitoSugar {
  private val header = "PostCode"

  "PostcodesLoader" should {
    "load all postcodes from the csv file" in {
      val result = PostcodesLoader.load("/po_box_postcodes_abp_49.csv")

      result.size should not be 0
      result.headOption should not be Some(header)
      // The number of postcodes should be between 45000 and 55000
      result.size should be > 45000
      result.size should be < 55000
    }

    "return exception if file path is empty" in {
      val exception = intercept[PostcodeLoaderException] {
        PostcodesLoader.load("")
      }

      exception.getMessage should include("Postcodes file path cannot be empty")
    }

    "return exception if file path is not csv" in {
      val exception = intercept[PostcodeLoaderException] {
        PostcodesLoader.load("/test.txt")
      }

      exception.getMessage should include("Postcodes file should be a csv file")
    }

    "return exception if an invalid postcode file is loaded" in {
      val exception = intercept[PostcodeLoaderException] {
        PostcodesLoader.load("/invalid_box_postcodes.csv")
      }

      exception.getMessage should include("Invalid entries found in the blacklisted postcodes file: AB10 1ZTInvalid-post-code,AB11 6NWInvalid-post-code")
    }
  }
}
