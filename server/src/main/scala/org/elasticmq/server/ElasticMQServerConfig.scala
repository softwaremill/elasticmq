package org.elasticmq.server

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigObject, ConfigValueType}
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
    defaultVisibilityTimeoutSeconds: Option[Long] = None,
    delaySeconds: Option[Long] = None,
    receiveMessageWaitSeconds: Option[Long] = None,
    deadLettersQueue: Option[CreateQueue] = None,
    maxReceiveCount: Option[Int] = None,
    isDeadLettersQueue: Boolean = false)

  val createQueues: List[CreateQueue] = {
    import scala.collection.JavaConversions._

    val maxReceiveCountKey = "maxReceiveCount"
    val deadLettersQueueKey = "deadLettersQueue"
    val defaultVisibilityTimeoutKey = "defaultVisibilityTimeout"
    val delayKey = "delay"
    val receiveMessageWaitKey = "receiveMessageWait"

    def fillQueueObj(name: String, co: ConfigObject, isDeadLettersQueue: Boolean = false): CreateQueue = {
      var createQueue = CreateQueue(name = name, isDeadLettersQueue = isDeadLettersQueue)
      val c = co.toConfig
      co.foreach {
        case (`defaultVisibilityTimeoutKey`, _) =>
          createQueue = createQueue.copy(defaultVisibilityTimeoutSeconds =
            Some(c.getDuration(defaultVisibilityTimeoutKey, TimeUnit.SECONDS)))
        case (`delayKey`, _) =>
          createQueue = createQueue.copy(delaySeconds =
            Some(c.getDuration(delayKey, TimeUnit.SECONDS)))
        case (`receiveMessageWaitKey`, _) =>
          createQueue = createQueue.copy(receiveMessageWaitSeconds =
            Some(c.getDuration(receiveMessageWaitKey, TimeUnit.SECONDS)))
        case (`maxReceiveCountKey`, _) =>
          createQueue = createQueue.copy(maxReceiveCount = Some(c.getInt(maxReceiveCountKey)))
        case (deadLettersQueueName, obj) if obj.valueType() == ConfigValueType.OBJECT =>
          createQueue = createQueue.copy(
            deadLettersQueue = Some(fillQueueObj(deadLettersQueueName, obj.asInstanceOf[ConfigObject],
              isDeadLettersQueue = true)))
      }

      if (isDeadLettersQueue && createQueue.maxReceiveCount.isEmpty) {
        throw new IllegalArgumentException("maxReceiveCount not set for dead letters queue " + name)
      }

      createQueue
    }

    config.getObject("queues").map { case (name, obj) =>
      fillQueueObj(name, obj.asInstanceOf[ConfigObject])
    }.toList
  }
}