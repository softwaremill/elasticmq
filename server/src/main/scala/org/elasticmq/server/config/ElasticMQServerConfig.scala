package org.elasticmq.server.config

import com.typesafe.config.Config
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import org.elasticmq.persistence.file.QueueConfigUtil
import org.elasticmq.persistence.sql.SqlQueuePersistenceConfig
import org.elasticmq.rest.sqs.AutoCreateQueuesConfig
import org.elasticmq.util.Logging
import org.elasticmq.{NodeAddress, RelaxedSQSLimits, StrictSQSLimits}

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class ElasticMQServerConfig(config: Config) extends Logging {
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

  private val queuesStorage = config.getConfig("queues-storage")
  val queuesStoragePath: String = queuesStorage.getString("path")
  val queuesStorageEnabled: Boolean = queuesStorage.getBoolean("enabled")

  private val awsConfig = config.getConfig("aws")
  val awsRegion: String = awsConfig.getString("region")
  val awsAccountId: String = awsConfig.getString("accountId")

  private def getSqlQueuePersistenceConfig = {
    val subConfig = config.getConfig("messages-storage")
    val enabled = subConfig.getBoolean("enabled")
    val driverClass = subConfig.getString("driver-class")
    val uri = subConfig.getString("uri")
    val username = subConfig.getString("username")
    val password = subConfig.getString("password")
    val pruneDataOnInit = subConfig.getBoolean("prune-data-on-init")

    SqlQueuePersistenceConfig(enabled, driverClass, uri, username, password, pruneDataOnInit)
  }

  val sqlQueuePersistenceConfig: SqlQueuePersistenceConfig = getSqlQueuePersistenceConfig

  val baseQueues: List[CreateQueueMetadata] = QueueConfigUtil.readPersistedQueuesFromConfig(config)

  val autoCreateQueues: AutoCreateQueuesConfig = parseAutoCreateQueuesConfig

  private def parseAutoCreateQueuesConfig: AutoCreateQueuesConfig = {
    if (!config.hasPath("auto-create-queues")) return AutoCreateQueuesConfig.disabled
    val subConfig = config.getConfig("auto-create-queues")
    val enabled = if (subConfig.hasPath("enabled")) subConfig.getBoolean("enabled") else false
    if (!enabled) return AutoCreateQueuesConfig.disabled
    val template =
      if (subConfig.hasPath("template")) parseQueueTemplate(subConfig.getConfig("template"))
      else CreateQueueMetadata(name = "")
    AutoCreateQueuesConfig(enabled = true, template = template)
  }

  private def parseQueueTemplate(c: Config): CreateQueueMetadata = {
    def getOptionalBoolean(k: String) = if (c.hasPath(k)) Some(c.getBoolean(k)) else None
    def getOptionalDuration(k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None
    def getOptionalString(k: String) = if (c.hasPath(k)) Some(c.getString(k)).filter(_.nonEmpty) else None
    def getOptionalTags(k: String): Map[String, String] =
      if (c.hasPath(k)) c.getObject(k).asScala.map { case (key, _) => key -> c.getString(k + '.' + key) }.toMap
      else Map.empty

    val isFifo = getOptionalBoolean("fifo").getOrElse(false)
    val deadLettersQueueKey = "deadLettersQueue"

    CreateQueueMetadata(
      name = "",
      defaultVisibilityTimeoutSeconds = getOptionalDuration("defaultVisibilityTimeout"),
      delaySeconds = getOptionalDuration("delay"),
      receiveMessageWaitSeconds = getOptionalDuration("receiveMessageWait"),
      deadLettersQueue =
        if (c.hasPath(deadLettersQueueKey))
          Some(
            DeadLettersQueue(
              c.getString(deadLettersQueueKey + ".name"),
              c.getInt(deadLettersQueueKey + ".maxReceiveCount")
            )
          )
        else None,
      isFifo = isFifo,
      hasContentBasedDeduplication = getOptionalBoolean("contentBasedDeduplication").getOrElse(false),
      copyMessagesTo = getOptionalString("copyTo"),
      moveMessagesTo = getOptionalString("moveTo"),
      tags = getOptionalTags("tags")
    )
  }
}
