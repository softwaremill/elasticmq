package org.elasticmq.actor

import org.elasticmq.msg._
import scala.reflect._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.msg.CreateQueue
import akka.actor.{Props, ActorRef}
import org.elasticmq.data.QueueAlreadyExists
import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.util.NowProvider
import org.elasticmq.actor.queue.QueueActor

class QueueManagerActor(nowProvider: NowProvider) extends ReplyingActor with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev = classTag[M[Unit]]

  private val queues = collection.mutable.HashMap[String, ActorRef]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]) = msg match {
    case CreateQueue(queueData) => {
      if (queues.contains(queueData.name)) {
        Left(new QueueAlreadyExists(queueData.name))
      } else {
        logger.info(s"Creating queue $queueData")
        val actor = context.actorOf(Props(new QueueActor(nowProvider, queueData)))
        queues(queueData.name) = actor
        Right(actor)
      }
    }

    case DeleteQueue(queueName) => {
      logger.info(s"Deleting queue $queueName")
      queues.remove(queueName).foreach(context.stop(_))
    }

    case LookupQueue(queueName) => queues.get(queueName)

    case ListQueues() => queues.keySet.toSeq
  }
}
