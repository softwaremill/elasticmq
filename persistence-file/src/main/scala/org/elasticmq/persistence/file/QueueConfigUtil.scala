package org.elasticmq.persistence.file

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import org.joda.time.DateTime

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.util.Try

object QueueConfigUtil {

  def readPersistedQueuesFromPath(queuesStoragePath: String): List[CreateQueueMetadata] =
    Try(ConfigFactory.parseFile(new File(queuesStoragePath)))
      .map(readPersistedQueuesFromConfig)
      .getOrElse(Nil)

  def readPersistedQueuesFromConfig(persistedQueuesConfig: Config): List[CreateQueueMetadata] =
    Try(persistedQueuesConfig
      .getObject("queues")
      .asScala.toMap)
      .map(getQueuesFromConfig(_))
      .getOrElse(Nil)

  def getQueuesFromConfig(queuesConfig: Map[String, ConfigValue]): List[CreateQueueMetadata] = {
    def getOptionalBoolean(c: Config, k: String) = if (c.hasPath(k)) Some(c.getBoolean(k)) else None

    def getOptionalLong(c: Config, k: String) = if (c.hasPath(k)) Some(c.getLong(k)) else None

    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None

    def getOptionalString(c: Config, k: String) = if (c.hasPath(k)) Some(c.getString(k)).filter(_.nonEmpty) else None

    def getOptionalTags(c: Config, k: String): Map[String, String] =
      if (c.hasPath(k)) c.getObject(k).asScala.map { case (key, _) => key -> c.getString(k + '.' + key) }.toMap
      else Map[String, String]()

    val deadLettersQueueKey = "deadLettersQueue"

    val now = new DateTime().toInstant.getMillis

    queuesConfig.map { case (n, v) =>
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
  }

  private def addSuffixWhenFifoQueue(queueName: String, isFifo: Boolean): String = {
    if (isFifo && !queueName.endsWith(".fifo")) queueName + ".fifo"
    else queueName
  }
}
