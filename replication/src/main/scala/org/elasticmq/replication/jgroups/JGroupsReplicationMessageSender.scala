package org.elasticmq.replication.jgroups

import org.elasticmq.replication.message.ReplicationMessage
import org.jgroups.blocks.{MessageDispatcher, ResponseMode, RequestOptions}
import org.jgroups.Message
import org.elasticmq.replication._
import org.elasticmq.marshalling.ObjectMarshaller

class JGroupsReplicationMessageSender(objectMarshaller: ObjectMarshaller,
                                      defaultCommandReplicationMode: CommandReplicationMode,
                                      messageDispatcher: MessageDispatcher)
  extends ReplicationMessageSender {

  def broadcast(replicationMessage: ReplicationMessage) {
    broadcast(replicationMessage, defaultCommandReplicationMode)
  }

  def broadcastDoNotWait(replicationMessage: ReplicationMessage) {
    broadcast(replicationMessage, DoNotWaitReplicationMode)
  }

  private def broadcast(replicationMessage: ReplicationMessage, commandReplicationMode: CommandReplicationMode) {
    val requestOptions = new RequestOptions(responseModeForReplicationMode(commandReplicationMode), 0)

    // The val _ is needed because of type inferencing problems
    val _ = messageDispatcher.castMessage(
      null,
      new Message(null, objectMarshaller.serialize(replicationMessage)),
      requestOptions)
  }

  private def responseModeForReplicationMode(commandReplicationMode: CommandReplicationMode): ResponseMode =
    commandReplicationMode match {
      case DoNotWaitReplicationMode => ResponseMode.GET_NONE
      case WaitForAnyReplicationMode => ResponseMode.GET_FIRST
      case WaitForMajorityReplicationMode => ResponseMode.GET_MAJORITY
      case WaitForAllReplicationMode => ResponseMode.GET_ALL
    }
}
