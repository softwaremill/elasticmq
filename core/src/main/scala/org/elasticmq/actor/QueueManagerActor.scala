package org.elasticmq.actor

import akka.actor.{ActorRef, Props}
import org.elasticmq.actor.queue.{QueueActor, QueuePersister}
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, DeleteQueue, _}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq._

import scala.reflect._

class QueueManagerActor(nowProvider: NowProvider, limits: Limits, queuePersister: Option[QueuePersister] = None) extends ReplyingActor with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev: ClassTag[QueueManagerMsg[Unit]] = classTag[M[Unit]]

  private val queues = collection.mutable.HashMap[String, ActorRef]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]): ReplyAction[T] =
    msg match {
      case CreateQueue(queueData) =>
        if (queues.contains(queueData.name)) {
          logger.debug(s"Cannot create queue, as it already exists: $queueData")
          Left(new QueueAlreadyExists(queueData.name))
        } else {
          logger.info(s"Creating queue $queueData")
          Limits.verifyQueueName(queueData.name, queueData.isFifo, limits) match {
            case Left(error) =>
              Left(QueueCreationError(queueData.name, error))
            case Right(_) =>
              val actor = createQueueActor(nowProvider, queueData, queuePersister)
              queues(queueData.name) = actor
              queuePersister.foreach(_.persist(queueData))
              Right(actor)
          }
        }

      case DeleteQueue(queueName) =>
        logger.info(s"Deleting queue $queueName")
        queues.remove(queueName).foreach(context.stop)
        queuePersister.foreach(_.remove(queueName))

      case LookupQueue(queueName) =>
        val result = queues.get(queueName)

        logger.debug(s"Looking up queue $queueName, found?: ${result.isDefined}")
        result

      case ListQueues() => queues.keySet.toSeq
    }

  protected def createQueueActor(nowProvider: NowProvider, queueData: QueueData, queuePersister: Option[QueuePersister]): ActorRef = {
    val deadLetterQueueActor = queueData.deadLettersQueue.flatMap { qd => queues.get(qd.name) }
    val copyMessagesToQueueActor = queueData.copyMessagesTo.flatMap { queueName => queues.get(queueName) }
    val moveMessagesToQueueActor = queueData.moveMessagesTo.flatMap { queueName => queues.get(queueName) }

    context.actorOf(
      Props(
        new QueueActor(
          nowProvider,
          queueData,
          deadLetterQueueActor,
          copyMessagesToQueueActor,
          moveMessagesToQueueActor,
          queuePersister
        )
      )
    )
  }
}
