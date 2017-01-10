package org.elasticmq.server

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import org.elasticmq.NodeAddress
import org.elasticmq.rest.sqs.SQSLimits

class ElasticMQServerConfig(config: Config) {

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
    NodeAddress(subConfig.getString("protocol"), subConfig.getString("host"), subConfig.getInt("port"),
      subConfig.getString("context-path"))
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
        SQSLimits.Relaxed
      } else if ("strict".equalsIgnoreCase(name)) {
        SQSLimits.Strict
      } else {
        throw new IllegalArgumentException("Unknown sqs-limits name: " + name)
      }
    }
  }

  val restSqs = new RestSqsConfiguration

  case class CreateQueue(name: String,
    defaultVisibilityTimeoutSeconds: Option[Long],
    delaySeconds: Option[Long],
    receiveMessageWaitSeconds: Option[Long],
    deadLettersQueue: Option[CreateQueue] = None,
    maxReceiveCount: Int = 1,
    isDeadLettersQueue: Boolean = false)

  private val maxReceiveCountLimit = 1000

  val createQueues: List[CreateQueue] = {
    import scala.collection.JavaConversions._

    val maxReceiveCountKey = "maxReceiveCount"
    val deadLettersQueueKey = "deadLettersQueue"

    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None

    def getOptionalString(c: Config, k: String) = if (c.hasPath(k)) Some(c.getString(k)) else None

    def fillQueueObj(c: Config, isDeadLettersQueue: Boolean = false): CreateQueue = {
      CreateQueue(
        name = c.getString("name"),
        defaultVisibilityTimeoutSeconds = getOptionalDuration(c, "defaultVisibilityTimeout"),
        delaySeconds = getOptionalDuration(c, "delay"),
        receiveMessageWaitSeconds = getOptionalDuration(c, "receiveMessageWait"),
        maxReceiveCount = if (c.hasPath(maxReceiveCountKey))
          math.min(c.getInt(maxReceiveCountKey), maxReceiveCountLimit) else 1,
        deadLettersQueue = if (c.hasPath(deadLettersQueueKey))
          Some(fillQueueObj(c.getConfig(deadLettersQueueKey), isDeadLettersQueue = true)) else None,
        isDeadLettersQueue = isDeadLettersQueue
      )
    }

    config.getObjectList("queues").map { co =>
      fillQueueObj(co.toConfig)
    }.toList
  }
}