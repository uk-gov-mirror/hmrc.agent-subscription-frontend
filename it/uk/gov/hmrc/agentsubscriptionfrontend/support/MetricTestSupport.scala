package uk.gov.hmrc.agentsubscriptionfrontend.support

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.Matchers
import org.scalatestplus.play.OneAppPerSuite

import scala.collection.JavaConversions

trait MetricTestSupport {
  self: OneAppPerSuite with Matchers =>

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- JavaConversions.asScalaIterator[String](registry.getMetrics.keySet().iterator())) {
      registry.remove(metric)
    }
    metricsRegistry = registry
  }

  def timerShouldExistAndBeUpdated(metricName: String): Unit = {
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

  def noMetricExpectedAtThisPoint(): Unit = {
    val meters = metricsRegistry.getMeters
    meters.size() shouldBe 0
  }
}
