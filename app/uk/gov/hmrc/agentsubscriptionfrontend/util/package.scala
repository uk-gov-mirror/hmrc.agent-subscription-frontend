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

package uk.gov.hmrc.agentsubscriptionfrontend

import cats.data.OptionT
import play.api.mvc.Result

import scala.concurrent.Future

package object util {

  implicit def toFuture(result: Result): Future[Result] = Future.successful(result)

  implicit class valueOps[A](val a: A) extends AnyVal {
    def toFuture: Future[A] = Future.successful(a)
  }
  implicit class OptOps[A](val a: Option[A]) extends AnyVal {
    def toOptionT: OptionT[Future, A] = OptionT(Future successful a)
  }
  implicit class OptFutureOps[A](val a: Future[Option[A]]) extends AnyVal {
    def toOptionT: OptionT[Future, A] = OptionT(a)
  }
}
