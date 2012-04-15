package org.elasticmq.replication.jgroups

import java.util.concurrent.atomic.AtomicReference
import org.jgroups._
import org.elasticmq.NodeAddress
import com.weiglewilczek.slf4s.Logging
import org.jgroups.blocks.RequestHandler
import org.elasticmq.replication.CommandApplier
import org.elasticmq.marshalling.ObjectMarshaller
import org.elasticmq.replication.message.{ReplicationMessage, ApplyCommands, SetMaster}

class JGroupsRequestHandler(objectMarshaller: ObjectMarshaller,
                            commandApplier: CommandApplier,
                            masterAddressRef: AtomicReference[Option[NodeAddress]],
                            myAddress: NodeAddress) extends RequestHandler with Logging {
  def handle(msg: Message) = {
    try {
      tryHandle(msg)
    } catch {
      case e => {
        // JGroups doesn't log exceptions that occure during request handling in any way.
        logger.error("Exception when handling a jgroups message", e)
        throw e
      }
    }
  }
  
  def tryHandle(msg: Message) = {
    val message = objectMarshaller.deserialize(msg.getBuffer).asInstanceOf[ReplicationMessage]

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
