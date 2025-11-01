package org.elasticmq.persistence.file

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue, QueueSorter}
import org.elasticmq.util.Logging

import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object QueueConfigUtil extends Logging {

  def readPersistedQueuesFromPath(queuesStoragePath: String): List[CreateQueueMetadata] =
    Try(ConfigFactory.parseFile(new File(queuesStoragePath)))
      .map(readPersistedQueuesFromConfig)
      .getOrElse(Nil)

  def readPersistedQueuesFromConfig(persistedQueuesConfig: Config): List[CreateQueueMetadata] = {
    Try(
      persistedQueuesConfig
        .getObject("queues")
        .asScala
        .toMap
    ) match {
      case Success(value) => getQueuesFromConfig(value)
      case Failure(ex)    => {
        logger.error("Failed to extract queue configuration", ex)
        throw new IllegalStateException(ex)
      }
    }
  }

  private def getQueuesFromConfig(queuesConfig: Map[String, ConfigValue]): List[CreateQueueMetadata] = {
    Try(getQueuesFromConfigUnsafe(queuesConfig)) match {
      case Success(value) => value
      case Failure(ex)    => {
        logger.error("Failed to create queues from config", ex)
        throw new IllegalStateException(ex)
      }
    }
  }

  private def getQueuesFromConfigUnsafe(queuesConfig: Map[String, ConfigValue]): List[CreateQueueMetadata] = {
    def getOptionalBoolean(c: Config, k: String) = if (c.hasPath(k)) Some(c.getBoolean(k)) else None

    def getOptionalLong(c: Config, k: String) = if (c.hasPath(k)) Some(c.getLong(k)) else None

    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None

    def getOptionalString(c: Config, k: String) = if (c.hasPath(k)) Some(c.getString(k)).filter(_.nonEmpty) else None

    def getOptionalTags(c: Config, k: String): Map[String, String] =
      if (c.hasPath(k)) c.getObject(k).asScala.map { case (key, _) => key -> c.getString(k + '.' + key) }.toMap
      else Map[String, String]()

    val deadLettersQueueKey = "deadLettersQueue"

    val now = OffsetDateTime.now().toInstant.toEpochMilli

    val unsortedQueues = queuesConfig.map { case (n, v) =>
      val c = v.asInstanceOf[ConfigObject].toConfig
      val isFifo = getOptionalBoolean(c, "fifo").getOrElse(false)
      CreateQueueMetadata(
        name = addSuffixWhenFifoQueue(n, isFifo),
        defaultVisibilityTimeoutSeconds = getOptionalDuration(c, "defaultVisibilityTimeout"),
        delaySeconds = getOptionalDuration(c, "delay"),
        receiveMessageWaitSeconds = getOptionalDuration(c, "receiveMessageWait"),
        created = getOptionalLong(c, "created").getOrElse(now),
        lastModified = getOptionalLong(c, "lastModified").getOrElse(now),
        deadLettersQueue = if (c.hasPath(deadLettersQueueKey)) {
          Some(
            DeadLettersQueue(
              c.getString(deadLettersQueueKey + ".name"),
              c.getInt(deadLettersQueueKey + ".maxReceiveCount")
            )
          )
        } else None,
        isFifo = isFifo,
        hasContentBasedDeduplication = getOptionalBoolean(c, "contentBasedDeduplication").getOrElse(false),
        copyMessagesTo = getOptionalString(c, "copyTo"),
        moveMessagesTo = getOptionalString(c, "moveTo"),
        tags = getOptionalTags(c, "tags")
      )
    }.toList
    QueueSorter.sortCreateQueues(unsortedQueues)
  }

  private def addSuffixWhenFifoQueue(queueName: String, isFifo: Boolean): String = {
    if (isFifo && !queueName.endsWith(".fifo")) queueName + ".fifo"
    else queueName
  }
}
