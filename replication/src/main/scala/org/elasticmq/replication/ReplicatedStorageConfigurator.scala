package org.elasticmq.replication

import org.elasticmq.storage.StorageCommandExecutor
import org.jgroups.JChannel
import java.util.concurrent.atomic.AtomicBoolean

class ReplicatedStorageConfigurator(delegate: StorageCommandExecutor) {
  def start(): ReplicatedStorage = {
    val channel = new JChannel();
    channel.setDiscardOwnMessages(true)

    val commandMarshaller = new JavaSerializationCommandMarshaller
    val nodeIsMaster = new AtomicBoolean(false)

    val commandResultReplicator = new JGroupsCommandResultReplicator(delegate, commandMarshaller, channel)
    val replicatedStorage = new JGroupsReplicatedStorage(nodeIsMaster, delegate, channel, commandResultReplicator)

    val jgroupsMessageReceiver = new JGroupsMessageReceiver(commandMarshaller, replicatedStorage, channel, nodeIsMaster)

    channel.setReceiver(jgroupsMessageReceiver)
    channel.connect("ElasticMQ")

    replicatedStorage
  }
}


