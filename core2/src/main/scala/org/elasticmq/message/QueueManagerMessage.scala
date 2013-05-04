package org.elasticmq.message

import org.elasticmq.data.{QueueAlreadyExists, QueueData}
import akka.actor.ActorRef
import org.elasticmq.actor.reply.Replyable

sealed trait QueueManagerMessage[T] extends Replyable[T]

case class CreateQueue(queueData: QueueData) extends QueueManagerMessage[Either[QueueAlreadyExists, ActorRef]]
case class DeleteQueue(queueName: String) extends QueueManagerMessage[Unit]
case class LookupQueue(queueName: String) extends QueueManagerMessage[Option[ActorRef]]
case class ListQueues() extends QueueManagerMessage[Seq[String]]