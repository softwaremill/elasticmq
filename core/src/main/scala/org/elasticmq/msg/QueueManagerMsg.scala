package org.elasticmq.msg

import akka.actor.ActorRef
import org.elasticmq.actor.reply.Replyable
import org.elasticmq.{CreateQueueData, ElasticMQError}

sealed trait QueueManagerMsg[T] extends Replyable[T]

case class CreateQueue(request: CreateQueueData) extends QueueManagerMsg[Either[ElasticMQError, ActorRef]]
case class DeleteQueue(queueName: String) extends QueueManagerMsg[Unit]
case class LookupQueue(queueName: String) extends QueueManagerMsg[Option[ActorRef]]
case class ListQueues() extends QueueManagerMsg[Seq[String]]
