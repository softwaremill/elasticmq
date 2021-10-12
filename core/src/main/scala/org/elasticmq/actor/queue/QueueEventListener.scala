package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.{ElasticMQError, QueueData}
import org.elasticmq.actor.reply.Replyable

sealed trait OperationStatus
case object OperationSuccessful extends OperationStatus
case object OperationUnsupported extends OperationStatus

sealed trait QueueEvent

case class Restore(queueManagerActor: ActorRef) extends QueueEvent with Replyable[Either[List[ElasticMQError], OperationStatus]]

case class QueueCreated(queue: QueueData) extends QueueEvent
case class QueueDeleted(queueName: String) extends QueueEvent
case class QueueMetadataUpdated(queue: QueueData) extends QueueEvent

case class QueueMessageAdded(queueName: String, message: InternalMessage) extends QueueEvent with Replyable[OperationStatus]
case class QueueMessageUpdated(queueName: String, message: InternalMessage) extends QueueEvent with Replyable[OperationStatus]
case class QueueMessageRemoved(queueName: String, messageId: String) extends QueueEvent with Replyable[OperationStatus]
