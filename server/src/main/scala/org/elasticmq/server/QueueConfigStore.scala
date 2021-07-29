package org.elasticmq.server

import com.typesafe.config.ConfigRenderOptions
import org.elasticmq.actor.queue.QueuePersister
import org.elasticmq.server.config.ElasticMQServerConfig
import org.elasticmq.{QueueMetadata, QueueData}
import pureconfig.ConfigWriter
import pureconfig.generic.auto._

import java.io.PrintWriter
import scala.collection.mutable

case class QueueConfigStore(config: ElasticMQServerConfig) extends QueuePersister {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  override def persist(queueData: QueueData): Unit = {
    queues.put(queueData.name, queueData)
    saveToConfigFile(queues.values.toList)
  }

  override def remove(queueName: String): Unit = {
    queues.remove(queueName)
    saveToConfigFile(queues.values.toList)
  }

  override def update(queueData: QueueData): Unit = {
    queues.remove(queueData.name)
    queues.put(queueData.name, queueData)
    saveToConfigFile(queues.values.toList)
  }

  private def saveToConfigFile(queues: List[QueueData]): Unit = {
    val metadata: String = queues
      .map(queue => (queue.name, toQueueConfig(queue)))
      .map(queueNameToConfig => {
        val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
        val str = ConfigWriter[QueueMetadata].to(queueNameToConfig._2).render(options)
        s" ${queueNameToConfig._1} {$str}\n"
      })
      .mkString("")
    val queuesConfig = s"queues {\n$metadata}"
    new PrintWriter(config.persisterOutputFile) {
      write(queuesConfig)
      close()
    }
  }

  private def toQueueConfig(queue: QueueData): QueueMetadata =
    QueueMetadata(
      queue.defaultVisibilityTimeout.seconds,
      queue.delay.getStandardSeconds,
      queue.receiveMessageWait.getStandardSeconds,
      queue.deadLettersQueue,
      queue.isFifo,
      queue.hasContentBasedDeduplication,
      queue.copyMessagesTo.getOrElse(""),
      queue.moveMessagesTo.getOrElse(""),
      queue.tags
    )
}
