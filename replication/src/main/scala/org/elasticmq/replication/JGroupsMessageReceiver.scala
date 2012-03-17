package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicReference
import org.jgroups._
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.{ApplyCommands, SetMaster, ReplicationMessageMarshaller}
import com.weiglewilczek.slf4s.Logging

class JGroupsMessageReceiver(messageMarshaller: ReplicationMessageMarshaller,
                             commandApplier: CommandApplier,
                             channel: JChannel,
                             masterAddressRef: AtomicReference[Option[NodeAddress]],
                             myAddress: NodeAddress) extends ReceiverAdapter with Logging {
  override def receive(msg: Message) {
    val message = messageMarshaller.deserialize(msg.getBuffer)
    
    message match {
      case SetMaster(masterAddress) => {
        logger.info("Setting master in %s to: %s".format(myAddress, masterAddress))
        masterAddressRef.set(Some(masterAddress))
      }
      case ApplyCommands(commands) => commands.foreach(commandApplier.apply(_))
    }
  }

  override def viewAccepted(view: View) {
    logger.info("Received new view in %s: [%s]".format(channel.getAddress, view))

    // The first node is always the master. If we are the master, sending out our address.
    if (view.getMembers.get(0) == channel.getAddress) {
      // Any blocking ops must be done in a separate thread!
      new Thread() {
        override def run() {
          logger.info("I am the master, broadcasting my address (%s)".format(myAddress))
          channel.send(new Message(null, SetMaster(myAddress)))
        }
      }.start()

      // The message isn't broadcast to this node
      masterAddressRef.set(Some(myAddress))
    }
  }
}
