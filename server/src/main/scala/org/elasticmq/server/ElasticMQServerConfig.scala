package org.elasticmq.server

import org.elasticmq.NodeAddress
import org.elasticmq.storage.squeryl.DBConfiguration
import java.io.File
import org.elasticmq.replication.{WaitForMajorityReplicationMode, CommandReplicationMode}
import org.elasticmq.rest.sqs.SQSLimits
import com.typesafe.config.{ConfigFactory, Config}
import scala.collection.JavaConverters._
import org.squeryl.internals.DatabaseAdapter

class ElasticMQServerConfig(config: Config) {
  // Configure main storage: either in-memory or database-backed

  sealed trait Storage

  case object InMemoryStorage extends Storage
  case class DatabaseStorage(dbConfiguration: DBConfiguration) extends Storage

  val storage: Storage = {
    val subConfig = config.getConfig("storage")
    val storageType = subConfig.getString("type")
    if ("in-memory".equalsIgnoreCase(storageType)) {
      InMemoryStorage
    } else if ("database".equalsIgnoreCase(storageType)) {
      val subSubConfig = subConfig.getConfig("database")
      val dbConfiguration = DBConfiguration(
        Thread.currentThread().getContextClassLoader
          .loadClass("org.squeryl.adapters." + subSubConfig.getString("adapter") + "Adapter")
          .newInstance()
          .asInstanceOf[DatabaseAdapter],
        subSubConfig.getString("jdbc-url"),
        subSubConfig.getString("driver-class"),
        Some((subSubConfig.getString("username"), subSubConfig.getString("password"))),
        subSubConfig.getBoolean("create"),
        subSubConfig.getBoolean("drop")
      )
      DatabaseStorage(dbConfiguration)
    } else {
      throw new IllegalArgumentException("Unknown storage type: " + storageType)
    }
  }

  // Configure the file command log (journal)

  class FileLogConfiguration {
    private val subConfig = config.getConfig("file-log")
    val enabled = subConfig.getBoolean("enabled")
    val storageDir = new File(subConfig.getString("storage-dir"))
    val rotateLogsAfterCommandWritten = subConfig.getInt("rotate-logs-after-command-written")
  }

  val fileLog: FileLogConfiguration = new FileLogConfiguration

  // What is the outside visible address of this ElasticMQ node (used by replication and rest-sqs)

  val nodeAddress = {
    val subConfig = config.getConfig("node-address")
    NodeAddress(subConfig.getString("protocol"), subConfig.getString("host"), subConfig.getInt("port"),
      subConfig.getString("context-path"))
  }

  // Replication

  sealed trait NodeDiscovery
  case object UDP extends NodeDiscovery
  case class TCP(initialMembers: List[String], replicationBindAddress: String = "localhost:7800") extends NodeDiscovery

  class ReplicationConfiguration {
    private val subConfig = config.getConfig("replication")
    val enabled = subConfig.getBoolean("enabled")
    val commandReplicationMode: CommandReplicationMode = WaitForMajorityReplicationMode
    val numberOfNodes = subConfig.getInt("number-of-nodes")

    private val nodeDiscoveryType = subConfig.getString("node-discovery.type")
    val nodeDiscovery: NodeDiscovery = if ("udp".equalsIgnoreCase(nodeDiscoveryType)) {
      UDP
    } else if ("tcp".equalsIgnoreCase(nodeDiscoveryType)) {
      TCP(
        subConfig.getStringList("node-discovery.tcp.initial-members").asScala.toList,
        subConfig.getString("node-discovery.tcp.replication-bind-address")
      )
    } else {
      throw new IllegalArgumentException("Unknown node discovery type: " + nodeDiscoveryType)
    }

    val customJGroupsStackConfigurationFile: Option[File] = if (subConfig.getBoolean("custom-jgroups-stack-configuration-file.enabled")) {
      Some(new File(subConfig.getString("custom-jgroups-stack-configuration-file.path")))
    } else {
      None
    }
  }

  val replication = new ReplicationConfiguration

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
}

object ElasticMQServerConfig {
  def load = new ElasticMQServerConfig(ConfigFactory.load("conf/elasticmq"))
}