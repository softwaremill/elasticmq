package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.actor.reply.Replyable
import org.elasticmq.{ElasticMQError, QueueData}

sealed trait OperationStatus
case object OperationSuccessful extends OperationStatus
case object OperationUnsupported extends OperationStatus

sealed trait QueueEvent

case class Restore(queueManagerActor: ActorRef) extends QueueEvent with Replyable[Either[List[ElasticMQError], OperationStatus]]

case class QueueCreated(queue: QueueData) extends QueueEvent
case class QueueDeleted(queueName: String) extends QueueEvent
case class QueueMetadataUpdated(queue: QueueData) extends QueueEvent

sealed trait QueueEventWithOperationStatus extends QueueEvent with Replyable[OperationStatus]

case class QueueMessageAdded(queueName: String, message: InternalMessage) extends QueueEventWithOperationStatus
case class QueueMessageUpdated(queueName: String, message: InternalMessage) extends QueueEventWithOperationStatus
case class QueueMessageRemoved(queueName: String, messageId: String) extends QueueEventWithOperationStatus
