package org.elasticmq.metrics

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.ActorRef
import akka.util.Timeout
import javax.management.openmbean._
import org.elasticmq.QueueStatistics
import org.elasticmq.metrics.QueuesMetrics.{queueDataNames, queueStatisticsCompositeType, queueStatisticsTabularType}
import org.elasticmq.util.NowProvider

import scala.concurrent.{Await, ExecutionContext}

trait QueuesMetricsMBean {
  def getQueueNames: Array[String]

  def getNumberOfMessagesInQueue(queueName: String): CompositeData

  def getNumberOfMessagesForAllQueues: TabularData
}

class QueuesMetrics(queueManagerActor: ActorRef) extends QueuesMetricsMBean {

  implicit val timeout: Timeout = Timeout(21, TimeUnit.SECONDS)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  override def getQueueNames: Array[String] = {
    Await.result(QueueMetricsOps.getQueueNames(queueManagerActor), timeout.duration).toArray
  }

  override def getNumberOfMessagesInQueue(queueName: String): CompositeData = {
    val queueStatistics = Await.result(QueueMetricsOps.getQueueStatistics(queueName, queueManagerActor, new NowProvider), timeout.duration)

    createCompositeDataForQueueStatistics(queueName, queueStatistics)
  }

  override def getNumberOfMessagesForAllQueues: TabularData = {
    val queuesStatistics = Await.result(QueueMetricsOps.getQueuesStatistics(queueManagerActor, new NowProvider), timeout.duration)
    val support = new TabularDataSupport(
      queueStatisticsTabularType
    )
    queuesStatistics.foreach {
      case (queueName, statistics) => support.put(createCompositeDataForQueueStatistics(queueName, statistics))
    }
    support
  }

  private def createCompositeDataForQueueStatistics(queueName: String, statistics: QueueStatistics) = {
    val queueDataValues: Array[Object] = Array(
      queueName,
      Long.box(statistics.approximateNumberOfVisibleMessages),
      Long.box(statistics.approximateNumberOfInvisibleMessages),
      Long.box(statistics.approximateNumberOfMessagesDelayed),
    )

    new CompositeDataSupport(queueStatisticsCompositeType, queueDataNames, queueDataValues)
  }

}

object QueuesMetrics {
  val queueDataNames: Array[String] = Array(
    "queueName",
    "ApproximateNumberOfMessages",
    "ApproximateNumberOfMessagesNotVisible",
    "ApproximateNumberOfMessagesDelayed"
  )

  val queueDataAttributeTypes: Array[OpenType[_]] = Array(
    SimpleType.STRING,
    SimpleType.LONG,
    SimpleType.LONG,
    SimpleType.LONG
  )

  val queueStatisticsCompositeType: CompositeType = new CompositeType(
    "queue data",
    "represents information about queue",
    queueDataNames,
    queueDataNames,
    queueDataAttributeTypes
  )

  val queueStatisticsTabularType: TabularType = new TabularType(
    "queue statistics",
    "statistics of messages in given queue",
    queueStatisticsCompositeType,
    Array("queueName")
  )
}