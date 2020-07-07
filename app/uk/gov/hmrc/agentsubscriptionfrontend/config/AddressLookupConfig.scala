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

package uk.gov.hmrc.agentsubscriptionfrontend.config

import javax.inject.Inject
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentsubscriptionfrontend.controllers.routes

class AddressLookupConfig @Inject()(appConfig: AppConfig, messagesApi: MessagesApi) {

  def config(continueUrl: String)(implicit lang: Lang) = {

    val cy = Lang("CY")

    val v2Config = s"""{
  "version": 2,
  "options": {
    "continueUrl": "$continueUrl",
    "includeHMRCBranding": true,
     "signOutHref": "http://tax.service.gov.uk${routes.SignedOutController.signOut().url}",
    "selectPageConfig": {
      "proposedListLimit": 30,
      "showSearchLinkAgain": true
    },
    "allowedCountryCodes": [
    "GB"
    ],
    "confirmPageConfig": {
      "showChangeLink": true,
      "showSubHeadingAndInfo": true,
      "showSearchAgainLink": false,
      "showConfirmChangeText": true
    },
     "timeoutConfig": {
      "timeoutAmount": ${appConfig.timeout},
      "timeoutUrl": "http://tax.service.gov.uk${routes.SignedOutController.timedOut().url}"
    }
  },
  "labels": {
  "en": {
    "appLevelLabels": {
    "navTitle": "${messagesApi("app.name")}"
    },
    "lookupPageLabels": {
      "title": "${messagesApi("address.lookup.title")}",
      "heading": "${messagesApi("address.lookup.header")}"
    }
  },
  "cy": {
    "appLevelLabels": {
    "navTitle": "${messagesApi("app.name")(cy)}"
    },
     "lookupPageLabels": {
      "title": "${messagesApi("address.lookup.title")(cy)}",
      "heading": "${messagesApi("address.lookup.header")(cy)}"
    }
   }
 }
}"""
    Json.parse(v2Config).as[JsObject]
  }
}
