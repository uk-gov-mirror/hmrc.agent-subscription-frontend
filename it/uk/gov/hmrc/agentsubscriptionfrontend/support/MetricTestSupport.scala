package uk.gov.hmrc.agentsubscriptionfrontend.support

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.{Assertion, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.collection.JavaConverters._

trait MetricTestSupport {
  self: GuiceOneAppPerSuite with Matchers =>

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- registry.getMetrics.keySet().iterator().asScala) {
      registry.remove(metric)
    }
    metricsRegistry = registry
  }

  def timerShouldExistAndBeUpdated(metricName: String): Assertion = {
    val timers = metricsRegistry.getTimers
    val metric = timers.get(s"Timer-$metricName")
    if (metric == null) throw new Exception(s"Metric [$metricName] not found, try one of ${timers.keySet()}")
    metric.getCount should be >= 1L
  }

  def metricShouldExistAndBeUpdated(metricNames: String*): Unit = {
    val meters = metricsRegistry.getMeters
    metricNames.foreach { metricName =>
      val metric = meters.get(metricName)
      if (metric == null) throw new Exception(s"Metric [$metricName] not found, try one of ${meters.keySet()}")
      metric.getCount should be >= 1L
    }
  }

  def noMetricExpectedAtThisPoint(): Assertion = {
    val meters = metricsRegistry.getMeters
    meters.size() shouldBe 0
  }
}
