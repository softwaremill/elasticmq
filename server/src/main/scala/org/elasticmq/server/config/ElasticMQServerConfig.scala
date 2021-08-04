package org.elasticmq.server.config

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.elasticmq.server.QueueSorter
import org.elasticmq.server.config.ElasticMQServerConfig.createQueuesFromConfig
import org.elasticmq.util.Logging
import org.elasticmq.{NodeAddress, RelaxedSQSLimits, StrictSQSLimits}

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class ElasticMQServerConfig(config: Config) extends Logging {
  // Configure main storage

  sealed trait Storage

  case object InMemoryStorage extends Storage

  val storage: Storage = {
    val subConfig = config.getConfig("storage")
    val storageType = subConfig.getString("type")
    if ("in-memory".equalsIgnoreCase(storageType)) {
      InMemoryStorage
    } else {
      throw new IllegalArgumentException("Unknown storage type: " + storageType)
    }
  }

  // What is the outside visible address of this ElasticMQ node (used by rest-sqs)

  val nodeAddress = {
    val subConfig = config.getConfig("node-address")
    NodeAddress(
      subConfig.getString("protocol"),
      subConfig.getString("host"),
      subConfig.getInt("port"),
      subConfig.getString("context-path")
    )
  }

  val generateNodeAddress = config.getBoolean("generate-node-address")

  // Optionally expose the REST SQS interface

  class RestSqsConfiguration {
    private val subConfig = config.getConfig("rest-sqs")
    val enabled = subConfig.getBoolean("enabled")
    val bindPort = subConfig.getInt("bind-port")
    val bindHostname = subConfig.getString("bind-hostname")
    val sqsLimits = {
      val name = subConfig.getString("sqs-limits")
      if ("relaxed".equalsIgnoreCase(name)) {
        RelaxedSQSLimits
      } else if ("strict".equalsIgnoreCase(name)) {
        StrictSQSLimits
      } else {
        throw new IllegalArgumentException("Unknown sqs-limits name: " + name)
      }
    }
  }

  val restSqs = new RestSqsConfiguration

  class RestStatisticsConfiguration {
    private val subConfig = config.getConfig("rest-stats")
    val enabled = subConfig.getBoolean("enabled")
    val bindPort = subConfig.getInt("bind-port")
    val bindHostname = subConfig.getString("bind-hostname")
  }

  val restStatisticsConfiguration = new RestStatisticsConfiguration

  private val queuesStorage = config.getConfig("queues-storage")
  val queuesStoragePath: String = queuesStorage.getString("path")
  val queuesStorageEnabled: Boolean = queuesStorage.getBoolean("enabled")

  def readQueuesToLoad(persistedQueues: Seq[CreateQueue] = readPersistedQueues()): Seq[CreateQueue] = {
    val persistedQueuesName = persistedQueues.map(_.name).toSet
    val baseQueuesConfig: mutable.Map[String, ConfigValue] = config
      .getObject("queues")
      .asScala
    val baseQueues = createQueuesFromConfig(baseQueuesConfig).filterNot(queue => persistedQueuesName.contains(queue.name))
    persistedQueues ++ baseQueues
  }

  private val persistedQueuesConfig: Option[Config] =
    if (queuesStorageEnabled)
      Try(ConfigFactory
        .parseFile(new File(queuesStoragePath)))
        .toOption
    else None

  def readPersistedQueues(persistedQueuesConfig: Option[Config] = persistedQueuesConfig): List[CreateQueue] =
    persistedQueuesConfig match {
      case Some(file) =>
        Try(file
          .getObject("queues")
          .asScala)
          .map(createQueuesFromConfig)
          .getOrElse(Nil)
      case None => Nil
    }

  private val awsConfig = config.getConfig("aws")
  val awsRegion: String = awsConfig.getString("region")
  val awsAccountId: String = awsConfig.getString("accountId")

}

object ElasticMQServerConfig {
  def createQueuesFromConfig(queuesConfig: mutable.Map[String, ConfigValue]): List[CreateQueue] = {
    def getOptionalBoolean(c: Config, k: String) = if (c.hasPath(k)) Some(c.getBoolean(k)) else None
    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None
    def getOptionalString(c: Config, k: String) = if (c.hasPath(k)) Some(c.getString(k)).filter(_.nonEmpty) else None

    def getOptionalTags(c: Config, k: String): Map[String, String] =
      if (c.hasPath(k)) c.getObject(k).asScala.map { case (key, _) => key -> c.getString(k + '.' + key) }.toMap
      else Map[String, String]()

    val deadLettersQueueKey = "deadLettersQueue"

    val unsortedCreateQueues = queuesConfig.map { case (n, v) =>
      val c = v.asInstanceOf[ConfigObject].toConfig
      val isFifo = getOptionalBoolean(c, "fifo").getOrElse(false)
      CreateQueue(
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

    QueueSorter.sortCreateQueues(unsortedCreateQueues)
  }

  private def addSuffixWhenFifoQueue(queueName: String, isFifo: Boolean): String = {
    if (isFifo && !queueName.endsWith(".fifo")) queueName + ".fifo"
    else queueName
  }
}
