package org.elasticmq.replication

import org.elasticmq.replication.message.ReplicationMessage

trait ReplicationMessageSender {
  def broadcast(replicationMessage: ReplicationMessage)
  def broadcastDoNotWait(replicationMessage: ReplicationMessage)
}
