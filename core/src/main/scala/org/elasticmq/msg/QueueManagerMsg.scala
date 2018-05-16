package org.elasticmq.msg

import akka.actor.ActorRef
import org.elasticmq.actor.reply.Replyable
import org.elasticmq.{ElasticMQError, QueueData}

sealed trait QueueManagerMsg[T] extends Replyable[T]

case class CreateQueue(queueData: QueueData) extends QueueManagerMsg[Either[ElasticMQError, ActorRef]]
case class DeleteQueue(queueName: String) extends QueueManagerMsg[Unit]
case class LookupQueue(queueName: String) extends QueueManagerMsg[Option[ActorRef]]
case class ListQueues() extends QueueManagerMsg[Seq[String]]
