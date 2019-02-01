/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.Nino

object TaxIdentifierFormatters {

  private val UtrMaxLength = 10

  def normalizeUtr(utrStr: String): Option[Utr] = {
    val formattedUtr = utrStr.replace(" ", "")
    def isNumber(str: String): Boolean = str.map(_.isDigit).reduceOption(_ && _).getOrElse(false)

    if (isNumber(formattedUtr) && formattedUtr.size == UtrMaxLength) Some(Utr(formattedUtr))
    else None
  }

  def prettify(arn: Arn): String = {
    val unapplyPattern = """([A-Z]ARN)(\d{3})(\d{4})""".r

    unapplyPattern
      .unapplySeq(arn.value)
      .map(_.mkString("-"))
      .getOrElse(throw new Exception(s"The arn contains an invalid value ${arn.value}"))
  }

  def prettify(utr: Utr): String =
    if (utr.value.trim.length == 10) {
      val (first, last) = utr.value.trim.splitAt(5)
      s"$first $last"
    } else {
      throw new Exception(s"The utr contains an invalid value ${utr.value}")
    }

  def normalizeNino(ninoStr: String): Option[Nino] = {
    val formattedNino = ninoStr
      .replaceAll("\\s", "")
      .toUpperCase

    if (Nino.isValid(formattedNino)) Some(Nino(formattedNino)) else None
  }
}
