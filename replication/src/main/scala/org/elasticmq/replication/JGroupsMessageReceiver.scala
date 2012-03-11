package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicBoolean
import org.jgroups.{JChannel, View, Message, ReceiverAdapter}

class JGroupsMessageReceiver(commandMarshaller: CommandMarshaller,
                             commandApplier: CommandApplier,
                             channel: JChannel,
                             isNodeMaster: AtomicBoolean) extends ReceiverAdapter {
  override def receive(msg: Message) {
    val commands = commandMarshaller.deserialize(msg.getBuffer)
    commands.foreach(commandApplier.apply(_))
  }

  override def viewAccepted(view: View) {
    // The first node is always the master
    val isMaster = channel.getAddress == view.getMembers.get(0)
    isNodeMaster.set(isMaster)
  }
}
