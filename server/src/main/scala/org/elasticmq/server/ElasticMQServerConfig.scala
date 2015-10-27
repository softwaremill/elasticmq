package org.elasticmq.server

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigObject, Config}
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

  // What is the outside visible address of this ElasticMQ node (used by replication and rest-sqs)

  val nodeAddress = {
    val subConfig = config.getConfig("node-address")
    NodeAddress(subConfig.getString("protocol"), subConfig.getString("host"), subConfig.getInt("port"),
      subConfig.getString("context-path"))
  }

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
    receiveMessageWaitSeconds: Option[Long])

  val createQueues: List[CreateQueue] = {
    def getOptionalDuration(c: Config, k: String) = if (c.hasPath(k)) Some(c.getDuration(k, TimeUnit.SECONDS)) else None

    import scala.collection.JavaConversions._
    config.getObject("queues").map { case (n, v) =>
      val c = v.asInstanceOf[ConfigObject].toConfig
      CreateQueue(
        n,
        getOptionalDuration(c, "defaultVisibilityTimeout"),
        getOptionalDuration(c, "delay"),
        getOptionalDuration(c, "receiveMessageWait")
      )
    }.toList
  }
}