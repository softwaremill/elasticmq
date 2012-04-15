package org.elasticmq.replication

import org.elasticmq.storage.StorageCommandExecutor
import org.jgroups.JChannel
import org.elasticmq.NodeAddress
import org.jgroups.protocols.pbcast.FLUSH
import org.jgroups.blocks.MessageDispatcher
import org.elasticmq.replication.jgroups._
import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.marshalling.JavaSerializationMarshaller

/**
 * @param myAddress Logical address of the node.
 * @param numberOfNodes (n) The number of nodes that this cluster will have. In case of a cluster partition, only
 * the partition with n/2+1 nodes will work, to avoid data corruption and receiving messages from two cluster
 * parititions.
 * @param createJChannel Method that will create the JGroups JChannel. By default will create a channel which uses
 * the standard (multicast) UDP stack.
 */
class ReplicatedStorageConfigurator(delegate: StorageCommandExecutor,
                                    myAddress: NodeAddress,
                                    commandReplicationMode: CommandReplicationMode,
                                    numberOfNodes: Int,
                                    createJChannel: () => JChannel = () => new JChannel()) {
  def start(): ReplicatedStorage = {
    val channel = createJChannel()
    channel.setDiscardOwnMessages(true)

    // We need flush so that when the master broadcasts his address after a new node joins, all nodes receive it.
    channel.getProtocolStack.addProtocol(new FLUSH())

    val objectMarshaller = new JavaSerializationMarshaller
    val masterAddressRef = new AtomicReference[Option[NodeAddress]](None)
    
    val clusterState = new ClusterState(numberOfNodes)

    val messageDispatcher = new MessageDispatcher(channel, null, null)
    val replicationMessageSender = new JGroupsReplicationMessageSender(objectMarshaller, commandReplicationMode,
      messageDispatcher)

    val commandResultReplicator = new CommandResultReplicator(delegate, replicationMessageSender)
    val replicatedStorage = new JGroupsReplicatedStorage(masterAddressRef, delegate, channel,
      commandResultReplicator, myAddress, clusterState)
    
    val jgroupsRequestHandler = new JGroupsRequestHandler(objectMarshaller, replicatedStorage,
      masterAddressRef, myAddress)
    val jgroupsMembershipListener = new JGroupsMembershipListener(channel, masterAddressRef, myAddress,
      replicationMessageSender, clusterState)
    
    val jgroupsStateTransferMessageListener = new JGroupsStateTransferMessageListener(delegate)

    messageDispatcher.setRequestHandler(jgroupsRequestHandler)
    messageDispatcher.setMembershipListener(jgroupsMembershipListener)
    messageDispatcher.setMessageListener(jgroupsStateTransferMessageListener)

    channel.connect("ElasticMQ")
    channel.getState(null, 0)

    replicatedStorage
  }
}


