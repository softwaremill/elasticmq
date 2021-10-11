package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.{ElasticMQError, QueueData}
import org.elasticmq.actor.reply.Replyable

trait OperationStatus
case object OperationSuccessful extends OperationStatus
case object OperationUnsupported extends OperationStatus

sealed trait QueueMetadataChanged

case class Restore(queueManagerActor: ActorRef) extends QueueMetadataChanged with Replyable[Either[List[ElasticMQError], OperationStatus]]

case class QueueCreated(queue: QueueData) extends QueueMetadataChanged
case class QueueDeleted(queueName: String) extends QueueMetadataChanged
case class QueueMetadataUpdated(queue: QueueData) extends QueueMetadataChanged

case class AddMessage(queueName: String, message: InternalMessage) extends QueueMetadataChanged with Replyable[OperationStatus]
case class UpdateMessage(queueName: String, message: InternalMessage) extends QueueMetadataChanged with Replyable[OperationStatus]
case class RemoveMessage(queueName: String, messageId: String) extends QueueMetadataChanged with Replyable[OperationStatus]
