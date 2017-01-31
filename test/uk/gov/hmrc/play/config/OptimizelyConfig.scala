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

package uk.gov.hmrc.play.config

// N.B. This trait and companion object already exist in play-ui.

// These versions exist to override them during unit testing to allow views
// that depend on them to run in tests that don't start a Play application.

trait OptimizelyConfig {
  def optimizelyBaseUrl : String
  def optimizelyProjectId : Option[String]
}

object OptimizelyConfig extends OptimizelyConfig {
  override lazy val optimizelyBaseUrl = ""
  override lazy val optimizelyProjectId = None
}
