package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.storage.StorageCommandExecutor
import org.jgroups.JChannel
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.JavaSerializationReplicationMessageMarshaller
import org.jgroups.protocols.pbcast.FLUSH
import org.jgroups.blocks.MessageDispatcher
import org.elasticmq.replication.jgroups._

class ReplicatedStorageConfigurator(delegate: StorageCommandExecutor,
                                    myAddress: NodeAddress,
                                    commandReplicationMode: CommandReplicationMode) {
  def start(): ReplicatedStorage = {
    val channel = new JChannel();
    channel.setDiscardOwnMessages(true)

    // We need flush so that when the master broadcasts his address after a new node joins, all nodes receive it.
    channel.getProtocolStack.addProtocol(new FLUSH())

    val messageMarshaller = new JavaSerializationReplicationMessageMarshaller
    val masterAddressRef = new AtomicReference[Option[NodeAddress]](None)

    val messageDispatcher = new MessageDispatcher(channel, null, null)
    val replicationMessageSender = new JGroupsReplicationMessageSender(messageMarshaller, commandReplicationMode,
      messageDispatcher)

    val commandResultReplicator = new CommandResultReplicator(delegate, replicationMessageSender)
    val replicatedStorage = new JGroupsReplicatedStorage(masterAddressRef, delegate, channel,
      commandResultReplicator, myAddress)
    
    val jgroupsRequestHandler = new JGroupsRequestHandler(messageMarshaller, replicatedStorage,
      masterAddressRef, myAddress)
    val jgroupsMembershipListener = new JGroupsMembershipListener(channel, masterAddressRef, myAddress,
      replicationMessageSender)
    
    val jgroupsStateTransferMessageListener = new JGroupsStateTransferMessageListener(delegate)

    messageDispatcher.setRequestHandler(jgroupsRequestHandler)
    messageDispatcher.setMembershipListener(jgroupsMembershipListener)
    messageDispatcher.setMessageListener(jgroupsStateTransferMessageListener)

    channel.connect("ElasticMQ")

    replicatedStorage
  }
}


