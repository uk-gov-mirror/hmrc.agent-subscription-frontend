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

package uk.gov.hmrc.agentsubscriptionfrontend.support

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.agentsubscriptionfrontend.models.IdentifyBusinessType

object Binders {

  implicit def businessTypeBinder(implicit stringBinder: QueryStringBindable[String]) =
    new QueryStringBindable[IdentifyBusinessType] {

      override def unbind(key: String, businessIdentifier: IdentifyBusinessType): String =
        stringBinder.unbind(key, businessIdentifier.key)

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, IdentifyBusinessType]] =
        stringBinder
          .bind("businessType", params)
          .map {
            case Right(input) => {
              IdentifyBusinessType(input) match {
                case IdentifyBusinessType.Undefined =>
                  Left("Submitted businessType value was invalid")
                case anyType => Right(anyType)
              }
            }
            case Left(noInputError) => Left(noInputError)
          }
    }
}
