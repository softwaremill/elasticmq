package org.elasticmq.persistence.file

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue, QueueSorter}
import org.elasticmq.util.Logging

import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object QueueConfigUtil extends Logging {

  /** Queue attribute keys, as written since 2.0. */
  object Keys {
    val DefaultVisibilityTimeout = "default-visibility-timeout"
    val Delay = "delay"
    val ReceiveMessageWait = "receive-message-wait"
    val DeadLettersQueue = "dead-letters-queue"
    val DeadLettersQueueName = "name"
    val DeadLettersQueueMaxReceiveCount = "max-receive-count"
    val Fifo = "fifo"
    val ContentBasedDeduplication = "content-based-deduplication"
    val CopyTo = "copy-to"
    val MoveTo = "move-to"
    val Tags = "tags"
    val MessageRetentionPeriod = "message-retention-period"
    val MaximumMessageSize = "maximum-message-size"
    val Created = "created"
    val LastModified = "last-modified"
  }

  /** Queue attribute keys used by 1.x, still read with a deprecation warning. */
  private val DeprecatedKeys = Map(
    Keys.DefaultVisibilityTimeout -> "defaultVisibilityTimeout",
    Keys.ReceiveMessageWait -> "receiveMessageWait",
    Keys.DeadLettersQueue -> "deadLettersQueue",
    Keys.DeadLettersQueueMaxReceiveCount -> "maxReceiveCount",
    Keys.ContentBasedDeduplication -> "contentBasedDeduplication",
    Keys.CopyTo -> "copyTo",
    Keys.MoveTo -> "moveTo",
    Keys.LastModified -> "lastModified"
  )

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

  /** Reads queue attributes (e.g. the `auto-create-queues.template` section) into a [[CreateQueueMetadata]] with an
    * empty name.
    */
  def readQueueAttributes(c: Config): CreateQueueMetadata = {
    val now = OffsetDateTime.now().toInstant.toEpochMilli
    readQueue("", c, now)
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
    val now = OffsetDateTime.now().toInstant.toEpochMilli

    val unsortedQueues = queuesConfig.map { case (n, v) =>
      val queue = readQueue(n, v.asInstanceOf[ConfigObject].toConfig, now)
      queue.copy(name = addSuffixWhenFifoQueue(n, queue.isFifo))
    }.toList
    QueueSorter.sortCreateQueues(unsortedQueues)
  }

  private def readQueue(name: String, c: Config, now: Long): CreateQueueMetadata = {
    def path(c: Config, k: String): Option[String] =
      DeprecatedKeys.get(k) match {
        case Some(deprecatedKey) => DeprecatedConfigKeys.resolve(c, k, deprecatedKey)
        case None                => Some(k).filter(c.hasPath)
      }

    def getOptionalBoolean(k: String) = path(c, k).map(c.getBoolean)
    def getOptionalLong(k: String) = path(c, k).map(c.getLong)
    def getOptionalDuration(k: String) = path(c, k).map(c.getDuration(_, TimeUnit.SECONDS))
    def getOptionalBytes(k: String) = path(c, k).map(c.getBytes(_).longValue)
    def getOptionalString(k: String) = path(c, k).map(c.getString).filter(_.nonEmpty)
    def getOptionalTags(k: String): Map[String, String] =
      path(c, k)
        .map(p => c.getObject(p).asScala.map { case (key, _) => key -> c.getConfig(p).getString(quote(key)) }.toMap)
        .getOrElse(Map.empty)

    val deadLettersQueue = path(c, Keys.DeadLettersQueue).map { p =>
      val dlq = c.getConfig(p)
      DeadLettersQueue(
        dlq.getString(Keys.DeadLettersQueueName),
        path(dlq, Keys.DeadLettersQueueMaxReceiveCount)
          .map(dlq.getInt)
          .getOrElse(throw new IllegalArgumentException(s"$p.${Keys.DeadLettersQueueMaxReceiveCount} is required"))
      )
    }

    CreateQueueMetadata(
      name = name,
      defaultVisibilityTimeoutSeconds = getOptionalDuration(Keys.DefaultVisibilityTimeout),
      delaySeconds = getOptionalDuration(Keys.Delay),
      receiveMessageWaitSeconds = getOptionalDuration(Keys.ReceiveMessageWait),
      created = getOptionalLong(Keys.Created).getOrElse(now),
      lastModified = getOptionalLong(Keys.LastModified).getOrElse(now),
      deadLettersQueue = deadLettersQueue,
      isFifo = getOptionalBoolean(Keys.Fifo).getOrElse(false),
      hasContentBasedDeduplication = getOptionalBoolean(Keys.ContentBasedDeduplication).getOrElse(false),
      copyMessagesTo = getOptionalString(Keys.CopyTo),
      moveMessagesTo = getOptionalString(Keys.MoveTo),
      tags = getOptionalTags(Keys.Tags),
      messageRetentionPeriodSeconds = getOptionalDuration(Keys.MessageRetentionPeriod),
      maximumMessageSize = getOptionalBytes(Keys.MaximumMessageSize).map { size =>
        if (size <= 0 || size > Int.MaxValue)
          throw new IllegalArgumentException(s"Invalid ${Keys.MaximumMessageSize}: $size")
        size.toInt
      }
    )
  }

  private def quote(key: String): String = "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def addSuffixWhenFifoQueue(queueName: String, isFifo: Boolean): String = {
    if (isFifo && !queueName.endsWith(".fifo")) queueName + ".fifo"
    else queueName
  }
}
