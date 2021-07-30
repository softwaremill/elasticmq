package org.elasticmq.server

import com.typesafe.config.ConfigRenderOptions
import org.elasticmq.actor.queue.QueuePersister
import org.elasticmq.server.config.ElasticMQServerConfig
import org.elasticmq.{DeadLettersQueueData, QueueData, QueueMetadata}
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigWriter}

import java.io.PrintWriter
import scala.collection.mutable

case class QueueConfigStore(config: ElasticMQServerConfig) extends QueuePersister {

  private implicit val queueMetadataHint: ProductHint[QueueMetadata] =
    ProductHint[QueueMetadata](ConfigFieldMapping(CamelCase, CamelCase))
  private implicit val deadLettersHint: ProductHint[DeadLettersQueueData] =
    ProductHint[DeadLettersQueueData](ConfigFieldMapping(CamelCase, CamelCase))

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

  def saveToConfigFile(queues: List[QueueData]): Unit = {
    val queuesConfig: String = prepareQueuesConfig(queues)
    new PrintWriter(config.queuesStoragePath) {
      write(queuesConfig)
      close()
    }
  }

  def prepareQueuesConfig(queues: List[QueueData]): String = {
    val queuesMetadata: String = queues
      .map(queue => (queue.name, toQueueConfig(queue)))
      .map(serializeQueueMetadata)
      .mkString("")
    s"queues {\n$queuesMetadata}"
  }

  private def serializeQueueMetadata(queueNameToMetadata: (String, QueueMetadata)): String = {
    val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
    val str = ConfigWriter[QueueMetadata].to(queueNameToMetadata._2).render(options)
    s" ${queueNameToMetadata._1} {$str}\n"
  }

  private def toQueueConfig(queue: QueueData): QueueMetadata =
    QueueMetadata(
      queue.defaultVisibilityTimeout.millis,
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
