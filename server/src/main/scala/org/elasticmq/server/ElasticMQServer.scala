package org.elasticmq.server

import com.weiglewilczek.slf4s.Logging
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.storage.squeryl.SquerylStorage
import org.elasticmq.storage.StorageCommandExecutor
import org.elasticmq.storage.filelog.{FileLogConfiguration, FileLogConfigurator}
import java.io.File
import org.elasticmq.replication.ReplicatedStorageConfigurator
import org.jgroups.JChannel
import org.elasticmq.rest.sqs.SQSRestServerFactory
import org.elasticmq.NodeBuilder
import java.net.InetSocketAddress
import org.elasticmq.rest.RestServer

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  def start() = {
    val start = System.currentTimeMillis()

    logger.info("Starting the ElasticMQ server ...")

    val baseStorage = createStorage()
    val withOptionalFileLog = optionallyWrapWithFileLog(baseStorage)
    val withOptionalReplication = optionallyStartReplication(withOptionalFileLog)

    val restServerOpt = optionallyStartRestSqs(withOptionalReplication)

    logger.info("=== ElasticMQ server started in %d ms ===".format(System.currentTimeMillis() - start))

    () => {
      restServerOpt.map(_.stop())
      withOptionalReplication.shutdown()
    }
  }

  private def createStorage() = {
    config.storage match {
      case config.InMemoryStorage => new InMemoryStorage()
      case config.DatabaseStorage(dbConfiguration) => new SquerylStorage(dbConfiguration)
    }
  }

  private def optionallyWrapWithFileLog(storage: StorageCommandExecutor) = {
    if (config.fileLog.enabled) {
      new FileLogConfigurator(storage,
        FileLogConfiguration(
          replaceBaseDirIfNeeded(config.fileLog.storageDir),
          config.fileLog.rotateLogsAfterCommandWritten)).start()
    } else {
      storage
    }
  }

  private def replaceBaseDirIfNeeded(file: File): File =  {
    val BaseDirToken = "$BASEDIR"
    val path = file.getPath
    if (path.contains(BaseDirToken)) {
      val newPath = path.replace(BaseDirToken, Environment.BaseDir)
      new File(newPath)
    } else {
      file
    }
  }

  private def optionallyStartReplication(storage: StorageCommandExecutor) = {
    if (config.replication.enabled) {
      new ReplicatedStorageConfigurator(
        storage,
        config.nodeAddress,
        config.replication.commandReplicationMode,
        config.replication.numberOfNodes,
        jchannelCreationFunction
      ).start()
    } else {
      storage
    }
  }

  private def jchannelCreationFunction: () => JChannel = {
    config.replication.customJGroupsStackConfigurationFile match {
      case Some(file) => {
        () => new JChannel(file)
      }
      case None => {
        config.replication.nodeDiscovery match {
          case config.UDP => () => new JChannel()
          case config.TCP(initialMembers, replicationBindAddress) => {
            () => {
              System.setProperty("jgroups.bind_addr", replicationBindAddress)
              System.setProperty("jgroups.tcpping.initial_hosts", membersListInJGroupsFormat(initialMembers))
              new JChannel("tcp.xml")
            }
          }
        }
      }
    }
  }

  private def membersListInJGroupsFormat(members: List[String]) = {
    members.map(member => {
      val parts = member.split(":")
      if (parts.size == 1) {
        member
      } else {
        parts(0) + "[" + parts(1) + "]"
      }
    }).mkString(",")
  }

  private def optionallyStartRestSqs(storage: StorageCommandExecutor): Option[RestServer] = {
    if (config.restSqs.enabled) {
      val client = NodeBuilder.withStorage(storage).nativeClient
      val server = SQSRestServerFactory.start(client,
        new InetSocketAddress(config.restSqs.bindHostname, config.restSqs.bindPort),
        config.nodeAddress)

      Some(server)
    } else {
      None
    }
  }
}
