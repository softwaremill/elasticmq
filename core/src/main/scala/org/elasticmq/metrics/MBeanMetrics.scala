package org.elasticmq.metrics

import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import akka.actor.ActorRef
import akka.util.Timeout
import javax.management.openmbean._
import org.elasticmq.QueueStatistics
import org.elasticmq.util.NowProvider

import scala.concurrent.{Await, ExecutionContext}

trait QueuesMetricsMBean {
  def getQueueNames: Array[String]

  def deleteQueue(queueName: String): Unit
}

class QueuesMetrics(queueManagerActor: ActorRef) extends QueuesMetricsMBean {

  implicit val timeout: Timeout = Timeout(21, TimeUnit.SECONDS)

  override def getQueueNames: Array[String] = {
    Await.result(QueuesMetricsOps.getQueueNames(queueManagerActor), timeout.duration).toArray
  }

  override def deleteQueue(queueName: String): Unit =
    Await.ready(QueuesMetricsOps.deleteQueue(queueName, queueManagerActor), timeout.duration)

}


trait QueueMetricsMBean {
  def getNumberOfMessagesInQueue(queueName: String): CompositeData

  def getNumberOfMessagesForAllQueues: TabularData
}

class QueueMetrics(queueManagerActor: ActorRef) extends QueueMetricsMBean {
  val queueDataNames: Array[String] = Array(
    "queueName",
    "visible",
    "invisible",
    "delayed"
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

  implicit val timeout: Timeout = Timeout(21, TimeUnit.SECONDS)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  override def getNumberOfMessagesInQueue(queueName: String): CompositeData = {
    val queueStatistics = Await.result(QueueMetricsOps.getNumberOfMessagesInQueue(queueName, queueManagerActor, new NowProvider), timeout.duration)

    createCompositeDataForQueueStatistics(queueName, queueStatistics)
  }

  override def getNumberOfMessagesForAllQueues: TabularData = {
    val queuesStatistics = Await.result(QueueMetricsOps.getNumberOfMessagesInAllQueues(queueManagerActor, new NowProvider), timeout.duration)
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