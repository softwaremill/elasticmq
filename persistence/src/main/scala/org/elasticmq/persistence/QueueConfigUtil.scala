package org.elasticmq.persistence

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import java.io.File
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.MapHasAsScala
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
      .map(getQueuesFromConfig)
      .getOrElse(Nil)

  def getQueuesToCreate(persistedQueues: List[CreateQueueMetadata], baseQueues: List[CreateQueueMetadata]): List[CreateQueueMetadata] = {
    val persistedQueuesName = persistedQueues.map(_.name).toSet
    persistedQueues ++ baseQueues.filterNot(queue => persistedQueuesName.contains(queue.name))
  }

  def getQueuesFromConfig(queuesConfig: Map[String, ConfigValue]): List[CreateQueueMetadata] = {
    def getOptionalBoolean(c: Config, k: String) = if (c.hasPath(k)) Some(c.getBoolean(k)) else None

    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None

    def getOptionalString(c: Config, k: String) = if (c.hasPath(k)) Some(c.getString(k)).filter(_.nonEmpty) else None

    def getOptionalTags(c: Config, k: String): Map[String, String] =
      if (c.hasPath(k)) c.getObject(k).asScala.map { case (key, _) => key -> c.getString(k + '.' + key) }.toMap
      else Map[String, String]()

    val deadLettersQueueKey = "deadLettersQueue"

    queuesConfig.map { case (n, v) =>
      val c = v.asInstanceOf[ConfigObject].toConfig
      val isFifo = getOptionalBoolean(c, "fifo").getOrElse(false)
      CreateQueueMetadata(
        name = addSuffixWhenFifoQueue(n, isFifo),
        defaultVisibilityTimeoutSeconds = getOptionalDuration(c, "defaultVisibilityTimeout"),
        delaySeconds = getOptionalDuration(c, "delay"),
        receiveMessageWaitSeconds = getOptionalDuration(c, "receiveMessageWait"),
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
