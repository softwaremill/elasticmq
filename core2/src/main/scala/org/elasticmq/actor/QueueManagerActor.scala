package org.elasticmq.actor

import org.elasticmq.message._
import scala.reflect._
import org.elasticmq.message.DeleteQueue
import org.elasticmq.message.CreateQueue
import akka.actor.{Props, ActorRef}
import org.elasticmq.data.QueueAlreadyExists
import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.actor.reply.ReplyingActor

class QueueManagerActor extends ReplyingActor with Logging {
  type M[X] = QueueManagerMessage[X]
  val ev = classTag[M[Unit]]

  private val queues = collection.mutable.HashMap[String, ActorRef]()

  def receiveAndReply[T](msg: QueueManagerMessage[T]) = msg match {
    case CreateQueue(queueData) => {
      if (queues.contains(queueData.name)) {
        Left(new QueueAlreadyExists(queueData.name))
      } else {
        logger.info(s"Creating queue $queueData")
        val actor = context.actorOf(Props(new QueueActor(queueData)))
        queues(queueData.name) = actor
        Right(actor)
      }
    }

    case DeleteQueue(queueName) => {
      logger.info(s"Deleting queue $queueName")
      queues.get(queueName).foreach(context.stop(_))
    }

    case LookupQueue(queueName) => queues.get(queueName)

    case ListQueues() => queues.keySet.toSeq
  }
}
