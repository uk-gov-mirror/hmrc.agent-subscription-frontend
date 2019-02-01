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

package uk.gov.hmrc.agentsubscriptionfrontend.config.blacklistedpostcodes

import scala.util.{Failure, Success, Try}

object PostcodesLoader {
  private val postcodeWithoutSpacesRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$".r

  def load(path: String) =
    Try {
      require(path.nonEmpty, "Postcodes file path cannot be empty")
      require(path.endsWith(".csv"), "Postcodes file should be a csv file")

      val header = 1
      val items = scala.io.Source.fromInputStream(PostcodesLoader.getClass.getResourceAsStream(path), "utf-8")
      items.getLines().drop(header).toSeq
    } match {
      case Success(postcodes) =>
        val invalidPostcodes =
          postcodes.filter(x => postcodeWithoutSpacesRegex.unapplySeq(formatPostcode(x)).isEmpty)

        if (invalidPostcodes.isEmpty)
          postcodes
        else
          throw new PostcodeLoaderException(
            s"Invalid entries found in the blacklisted postcodes file: ${invalidPostcodes.mkString(",")}")
      case Failure(ex) =>
        throw new PostcodeLoaderException(ex.getMessage)
    }

  def formatPostcode(p: String) = Option(p).map(_.replace(" ", "").toUpperCase).orNull

  final class PostcodeLoaderException(message: String)
      extends Exception(s"Unknown error code from agent-subscription while loading postcodes: $message")
}
