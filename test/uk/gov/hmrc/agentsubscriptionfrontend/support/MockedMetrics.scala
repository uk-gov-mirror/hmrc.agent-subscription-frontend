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

import com.codahale.metrics.{Meter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfterEach, Suite}

trait MockedMetrics extends ResettingMockitoSugar with BeforeAndAfterEach {
  this: Suite =>

  protected val mockMetrics = resettingMock[Metrics]
  protected val mockMetricsRegistry = resettingMock[MetricRegistry]
  protected val meters = new java.util.TreeMap[String, Meter]()

  override protected def beforeEach = {
    super.beforeEach()
    when(mockMetrics.defaultRegistry).thenReturn(mockMetricsRegistry)
    when(mockMetricsRegistry.getMeters).thenReturn(meters)
  }

  protected def mockMetrics(metricName: String): Unit =
    addMeter(metricName)

  protected def verifyMetricCalled(metricName: String, count: Int = 1) =
    verify(getMeter(metricName), times(count)).mark

  private def getMeter(metricName: String) = meters.get(metricName)
  private def addMeter(metricName: String): Meter = {
    meters.putIfAbsent(metricName, resettingMock[Meter])
    meters.get(metricName)
  }
}
