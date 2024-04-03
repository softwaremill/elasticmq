package org.elasticmq.persistence.file

import com.typesafe.config.ConfigRenderOptions
import org.elasticmq.{DeadLettersQueueData, QueueData}
import pureconfig.ConfigWriter

import java.io.PrintWriter

object QueuePersister {

  private implicit val deadLettersWriter: ConfigWriter[DeadLettersQueueData] =
    ConfigWriter.forProduct2[DeadLettersQueueData, String, Int]("name", "maxReceiveCount")(deadLetterQueueData =>
      (deadLetterQueueData.name, deadLetterQueueData.maxReceiveCount)
    )
  private implicit val queueMetadataWriter: ConfigWriter[QueueMetadata] =
    ConfigWriter.forProduct9[QueueMetadata, Long, Long, Long, Option[
      DeadLettersQueueData
    ], Boolean, Boolean, String, String, Map[String, String]](
      "defaultVisibilityTimeout",
      "delay",
      "receiveMessageWait",
      "deadLettersQueue",
      "fifo",
      "contentBasedDeduplication",
      "copyTo",
      "moveTo",
      "tags"
    )(qm =>
      (
        qm.defaultVisibilityTimeout,
        qm.delay,
        qm.receiveMessageWait,
        qm.deadLettersQueue,
        qm.fifo,
        qm.contentBasedDeduplication,
        qm.copyTo,
        qm.moveTo,
        qm.tags
      )
    )

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
