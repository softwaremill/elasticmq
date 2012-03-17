package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicReference
import org.jgroups._
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.{ApplyCommands, SetMaster, ReplicationMessageMarshaller}
import com.weiglewilczek.slf4s.Logging
import org.jgroups.blocks.RequestHandler

class JGroupsRequestHandler(messageMarshaller: ReplicationMessageMarshaller,
                             commandApplier: CommandApplier,
                             masterAddressRef: AtomicReference[Option[NodeAddress]],
                             myAddress: NodeAddress) extends RequestHandler with Logging {
  def handle(msg: Message) = {
    val message = messageMarshaller.deserialize(msg.getBuffer)
    
    message match {
      case SetMaster(masterAddress) => {
        logger.info("Setting master in %s to: %s".format(myAddress, masterAddress))
        masterAddressRef.set(Some(masterAddress))
      }
      case ApplyCommands(commands) => commands.foreach(commandApplier.apply(_))
    }

    // We must return something so that the caller knows the message is handled.
    null
  }
}
