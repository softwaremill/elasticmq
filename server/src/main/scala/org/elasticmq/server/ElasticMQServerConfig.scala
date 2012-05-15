package org.elasticmq.server

import com.twitter.util.Config
import com.twitter.ostrich.admin.RuntimeEnvironment
import org.elasticmq.NodeAddress
import org.elasticmq.storage.squeryl.DBConfiguration
import java.io.File
import org.elasticmq.replication.{WaitForMajorityReplicationMode, CommandReplicationMode}

class ElasticMQServerConfig extends Config[RuntimeEnvironment => ElasticMQServer] {
  // Configure main storage: either in-memory or database-backed

  sealed trait Storage

  case object InMemoryStorage extends Storage
  case class DatabaseStorage(dbConfiguration: DBConfiguration) extends Storage

  var storage: Storage = InMemoryStorage

  // Configure the file command log

  class FileLogConfiguration {
    var enabled = true
    var storageDir = new File("$BASEDIR" + File.separator + "data")
    var rotateLogsAfterCommandWritten = 10000
  }

  var fileLog: FileLogConfiguration = new FileLogConfiguration

  // What is the outside visible address of this ElasticMQ node (used by replication and rest-sqs)

  var nodeAddress = NodeAddress("localhost:9324")

  // Replication

  sealed trait NodeDiscovery
  case object UDP extends NodeDiscovery
  case class TCP(initialMembers: List[String]) extends NodeDiscovery

  class ReplicationConfiguration {
    var enabled = false
    var commandReplicationMode: CommandReplicationMode = WaitForMajorityReplicationMode
    var numberOfNodes = 3
    var nodeDiscovery: NodeDiscovery = UDP
    var customJGroupsStackConfigurationFile: Option[File] = None
  }

  var replication = new ReplicationConfiguration

  // Optionally expose the REST SQS interface

  class RestSqsConfiguration {
    var enabled = true
    var bindPort = 9324
    var bindHostname = "0.0.0.0"
  }

  var restSqs = new RestSqsConfiguration

  // End

  def apply() = { (runtime: RuntimeEnvironment) =>
    new ElasticMQServer(this)
  }
}
