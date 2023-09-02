package org.elasticmq.persistence.file

import com.typesafe.config.ConfigRenderOptions
import org.elasticmq.{DeadLettersQueueData, QueueData}
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigWriter}

import java.io.PrintWriter

object QueuePersister {

  private implicit val queueMetadataHint: ProductHint[QueueMetadata] =
    ProductHint[QueueMetadata](ConfigFieldMapping(CamelCase, CamelCase))
  private implicit val deadLettersHint: ProductHint[DeadLettersQueueData] =
    ProductHint[DeadLettersQueueData](ConfigFieldMapping(CamelCase, CamelCase))

  def saveToConfigFile(queues: List[QueueData], storagePath: String): Unit = {
    val queuesConfig: String = prepareQueuesConfig(queues)
    new PrintWriter(storagePath) {
      write(queuesConfig)
      close()
    }
  }

  def prepareQueuesConfig(queues: List[QueueData]): String = {
    val queuesMetadata: String = queues
      .map(queue => (quote(queue.name), toQueueConfig(queue)))
      .map(serializeQueueMetadata)
      .mkString("")
    s"queues {\n$queuesMetadata}"
  }

  private def quote(name: String): String = s""""$name""""

  private def serializeQueueMetadata(queueNameToMetadata: (String, QueueMetadata)): String = {
    val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
    val str = ConfigWriter[QueueMetadata].to(queueNameToMetadata._2).render(options)
    s" ${queueNameToMetadata._1} {$str}\n"
  }

  private def toQueueConfig(queue: QueueData): QueueMetadata =
    QueueMetadata(
      queue.defaultVisibilityTimeout.millis,
      queue.delay.toMillis,
      queue.receiveMessageWait.toMillis,
      queue.deadLettersQueue,
      queue.isFifo,
      queue.hasContentBasedDeduplication,
      queue.copyMessagesTo.getOrElse(""),
      queue.moveMessagesTo.getOrElse(""),
      queue.tags
    )

}

private case class QueueMetadata(
    defaultVisibilityTimeout: Long,
    delay: Long,
    receiveMessageWait: Long,
    deadLettersQueue: Option[DeadLettersQueueData],
    fifo: Boolean,
    contentBasedDeduplication: Boolean,
    copyTo: String,
    moveTo: String,
    tags: Map[String, String]
)
