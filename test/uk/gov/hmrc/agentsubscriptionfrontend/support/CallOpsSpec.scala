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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import java.net.URLEncoder

import org.scalatest.{Matchers, WordSpec}

class CallOpsSpec extends WordSpec with Matchers {
  "addParamsToUrl" should {
    val url = "http://localhost:1/"

    "return same url when no params provided and " when {
      "no query exists" in {
        CallOps.addParamsToUrl(url) shouldBe url
      }

      "query exists" in {
        CallOps.addParamsToUrl(s"$url?foo=bar") shouldBe s"$url?foo=bar"
      }

      "there is one parameter but with no value" in {
        CallOps.addParamsToUrl(url, "foo" -> None) shouldBe url
      }
    }

    "adds params to existing url" when {
      "there is no query part" in {
        CallOps.addParamsToUrl(url, "foo" -> Some("bar?")) shouldBe s"$url?foo=bar%3F"
      }

      "there is an existing query part" in {
        CallOps.addParamsToUrl(s"$url?foo=bar", "baz" -> Some("qwaggly")) shouldBe s"$url?foo=bar&baz=qwaggly"
      }

      "the url ends with ?" in {
        CallOps.addParamsToUrl(s"$url?", "foo" -> Some("bar")) shouldBe s"$url?foo=bar"
      }

      "the url ends with &" in {
        CallOps.addParamsToUrl(s"$url?foo=bar&", "baz" -> Some("qwaggly")) shouldBe s"$url?foo=bar&baz=qwaggly"
      }
    }
  }
}
