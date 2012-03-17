package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.storage.StorageCommandExecutor
import org.jgroups.JChannel
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.JavaSerializationReplicationMessageMarshaller
import org.jgroups.protocols.pbcast.FLUSH

class ReplicatedStorageConfigurator(delegate: StorageCommandExecutor, myAddress: NodeAddress) {
  def start(): ReplicatedStorage = {
    val channel = new JChannel();
    channel.setDiscardOwnMessages(true)

    val messageMarshaller = new JavaSerializationReplicationMessageMarshaller
    val masterAddressRef = new AtomicReference[Option[NodeAddress]](None)

    val commandResultReplicator = new JGroupsCommandResultReplicator(delegate, messageMarshaller, channel)
    val replicatedStorage = new JGroupsReplicatedStorage(masterAddressRef, delegate, channel,
      commandResultReplicator, myAddress)

    val jgroupsMessageReceiver = new JGroupsMessageReceiver(messageMarshaller, replicatedStorage, channel,
      masterAddressRef, myAddress)

    channel.setReceiver(jgroupsMessageReceiver)
    // We need flush so that when the master broadcasts his address after a new node joins, all nodes receive it.
    channel.getProtocolStack.addProtocol(new FLUSH())
    channel.connect("ElasticMQ")

    replicatedStorage
  }
}


