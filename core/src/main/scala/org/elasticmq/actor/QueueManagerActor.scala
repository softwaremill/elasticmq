package org.elasticmq.actor

import org.elasticmq.msg._

import scala.reflect._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.msg.CreateQueue
import akka.actor.{ActorRef, Props}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.actor.queue.QueueActor
import org.elasticmq.{QueueAlreadyExists, QueueData}
import org.elasticmq.actor.reply._

class QueueManagerActor(nowProvider: NowProvider) extends ReplyingActor with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev = classTag[M[Unit]]

  private val queues = collection.mutable.HashMap[String, ActorRef]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]): ReplyAction[T] = msg match {
    case CreateQueue(queueData) => {
      if (queues.contains(queueData.name)) {
        logger.debug(s"Cannot create queue, as it already exists: $queueData")
        Left(new QueueAlreadyExists(queueData.name))
      } else {
        logger.info(s"Creating queue $queueData")
        val actor = createQueueActor(nowProvider, queueData)
        queues(queueData.name) = actor
        Right(actor)
      }
    }

    case DeleteQueue(queueName) => {
      logger.info(s"Deleting queue $queueName")
      queues.remove(queueName).foreach(context.stop(_))
    }

    case LookupQueue(queueName) => {
      val result = queues.get(queueName)
      logger.debug(s"Looking up queue $queueName, found?: ${result.isDefined}")
      result
    }

    case ListQueues() => queues.keySet.toSeq
  }

  private def createQueueActor(nowProvider: NowProvider, queueData: QueueData): ActorRef = {
    val deadLetterQueueActor = queueData.deadLettersQueue.map { qd =>
      queues.getOrElse(qd.name, {
        val actor = context.actorOf(Props(new QueueActor(
          nowProvider, qd, qd.deadLettersQueue.map(createQueueActor(nowProvider, _)))))
        queues(qd.name) = actor
        actor
      })
    }
    context.actorOf(Props(new QueueActor(nowProvider, queueData, deadLetterQueueActor)))
  }
}
