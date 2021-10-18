package org.elasticmq.server.config

import com.typesafe.config.Config
import org.elasticmq.persistence.sql.SqlQueuePersistenceConfig
import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.persistence.file.QueueConfigUtil
import org.elasticmq.util.Logging
import org.elasticmq.{NodeAddress, RelaxedSQSLimits, StrictSQSLimits}

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
  val baseQueues: List[CreateQueueMetadata] = {
    if (queuesStorageEnabled)
      QueueConfigUtil.readPersistedQueuesFromConfig(config)
    else
      Nil
  }

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
}
