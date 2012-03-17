package org.elasticmq.replication.message

import org.elasticmq.NodeAddress
import org.elasticmq.storage.IdempotentMutativeCommand

sealed trait ReplicationMessage

case class SetMaster(nodeAddress: NodeAddress) extends ReplicationMessage
case class ApplyCommands(commands: Seq[IdempotentMutativeCommand[_]]) extends ReplicationMessage
