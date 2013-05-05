package org.elasticmq.replication.jgroups

import java.util.concurrent.atomic.AtomicReference
import org.jgroups._
import org.elasticmq.NodeAddress
import org.elasticmq.replication.message.SetMaster
import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.replication.{ClusterState, ReplicationMessageSender}
import scala.collection.JavaConverters._

class JGroupsMembershipListener(channel: JChannel,
                                masterAddressRef: AtomicReference[Option[NodeAddress]],
                                myAddress: NodeAddress,
                                replicationMessageSender: ReplicationMessageSender,
                                clusterState: ClusterState) extends MembershipListener with Logging {
  def viewAccepted(view: View) {
    logger.info("Received new view in %s: [%s]".format(channel.getAddress, view))

    clusterState.currentNumberOfNodes = view.getMembers.size()
    broadcastAddressIfMaster(view)
    
    if (view.isInstanceOf[MergeView]) {
      requestStateTransferIfNeeded(view.asInstanceOf[MergeView]) 
    }
  }

  private def broadcastAddressIfMaster(view: View) {
    // The first node is always the master. If we are the master, sending out our address.
    if (view.getMembers.get(0) == channel.getAddress) {
      // Any blocking ops must be done in a separate thread!
      new Thread() {
        override def run() {
          logger.info("I am the master, broadcasting my address (%s)".format(myAddress))
          replicationMessageSender.broadcastDoNotWait(SetMaster(myAddress))
        }
      }.start()

      // The msg isn't broadcast to this node
      masterAddressRef.set(Some(myAddress))
    }
  }
  
  private def requestStateTransferIfNeeded(view: MergeView) {
    val primaryParition = findPrimaryPartition(view)
    val thisPartition = view.getSubgroups.asScala.find(_.getMembers.contains(channel.getAddress)).get

    if (primaryParition != thisPartition) {
      new Thread() {
        override def run() {
          logger.info("Requesting state transfer in (%s) from primary partition %s"
            .format(channel.getAddress, primaryParition))

          channel.getState(primaryParition.getMembers.get(0), 0L)
        }
      }.start()
    }
  }
  
  private def findPrimaryPartition(view: MergeView): View = {
    val subgroups = view.getSubgroups.asScala
    subgroups.find(_.getMembers.size() >= clusterState.minimumNumberOfNodes) match {
      // First we try to find a partition which has at least half + 1 of the members. If it exists, it was active
      // all the time and contains the latest state.
      case Some(primaryPartition) => primaryPartition
      // Otherwise we get first of the biggest subgroups - it is most likely to have most up-to-date state 
      case None => subgroups.sortBy(- _.getMembers.size()).head
    }
  }

  def suspect(suspected_mbr: Address) {}

  def block() {}

  def unblock() {}
}
