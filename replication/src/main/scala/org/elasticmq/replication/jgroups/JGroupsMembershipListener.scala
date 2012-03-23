package org.elasticmq.replication.jgroups

import java.util.concurrent.atomic.AtomicReference
import org.jgroups._
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.SetMaster
import com.weiglewilczek.slf4s.Logging
import org.elasticmq.replication.ReplicationMessageSender

class JGroupsMembershipListener(channel: JChannel,
                                masterAddressRef: AtomicReference[Option[NodeAddress]],
                                myAddress: NodeAddress,
                                replicationMessageSender: ReplicationMessageSender) extends MembershipListener with Logging {
  def viewAccepted(view: View) {
    logger.info("Received new view in %s: [%s]".format(channel.getAddress, view))

    // The first node is always the master. If we are the master, sending out our address.
    if (view.getMembers.get(0) == channel.getAddress) {
      // Any blocking ops must be done in a separate thread!
      new Thread() {
        override def run() {
          logger.info("I am the master, broadcasting my address (%s)".format(myAddress))
          replicationMessageSender.broadcastDoNotWait(SetMaster(myAddress))
        }
      }.start()

      // The message isn't broadcast to this node
      masterAddressRef.set(Some(myAddress))
    } else {
      // We are not the master, requesting state transfer.
      channel.getState(null, 0)
    }
  }

  def suspect(suspected_mbr: Address) {}

  def block() {}

  def unblock() {}
}
