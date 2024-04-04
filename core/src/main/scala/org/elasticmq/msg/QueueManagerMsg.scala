package org.elasticmq.msg

import org.apache.pekko.actor.ActorRef
import org.elasticmq.actor.reply.Replyable
import org.elasticmq.{CreateQueueData, ElasticMQError}

sealed trait QueueManagerMsg[T] extends Replyable[T]

case class CreateQueue(request: CreateQueueData) extends QueueManagerMsg[Either[ElasticMQError, ActorRef]]
case class DeleteQueue(queueName: String) extends QueueManagerMsg[Unit]
case class LookupQueue(queueName: String) extends QueueManagerMsg[Option[ActorRef]]
case class ListQueues() extends QueueManagerMsg[Seq[String]]
case class ListDeadLetterSourceQueues(queueName: String) extends QueueManagerMsg[List[String]]
case class StartMessageMoveTask(
    sourceQueue: ActorRef,
    destinationQueue: Option[ActorRef],
    maxNumberOfMessagesPerSecond: Option[Int]
) extends QueueManagerMsg[Either[ElasticMQError, MessageMoveTaskId]]
case class MessageMoveTaskFinished(
    taskHandle: MessageMoveTaskId
) extends QueueManagerMsg[Unit]
case class CancelMessageMoveTask(
    taskHandle: MessageMoveTaskId
) extends QueueManagerMsg[Either[ElasticMQError, Int]]
