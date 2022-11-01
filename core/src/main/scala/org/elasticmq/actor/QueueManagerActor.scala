package org.elasticmq.actor

import akka.actor.{ActorRef, Props}
import org.elasticmq._
import org.elasticmq.actor.queue.{QueueActor, QueueEvent}
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.util.{Logging, NowProvider}

import scala.reflect._

class QueueManagerActor(nowProvider: NowProvider, limits: Limits, queueEventListener: Option[ActorRef])
    extends ReplyingActor
    with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev: ClassTag[QueueManagerMsg[Unit]] = classTag[M[Unit]]

  case class QueueMetadata(actorRef: ActorRef, queueData: QueueData)
  private val queues = collection.mutable.HashMap[String, QueueMetadata]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]): ReplyAction[T] =
    msg match {
      case CreateQueue(request) =>
        queues.get(request.name) match {
          case Some(metadata) =>
            if (sameCreateQueueData(metadata.queueData, request)) {
              logger.debug(s"Queue already exists: $request, returning existing actor")
              Right(metadata.actorRef)
            } else {
              logger.debug(
                s"Cannot create a queue with existing name and different parameters: $request, existing: ${metadata.queueData}"
              )
              Left(new QueueAlreadyExists(request.name))
            }
          case None =>
            logger.info(s"Creating queue $request")
            Limits.verifyQueueName(request.name, request.isFifo, limits) match {
              case Left(error) =>
                Left(InvalidParameterValue(request.name, error))
              case Right(_) =>
                val queueData = request.toQueueData
                val actor = createQueueActor(nowProvider, queueData, queueEventListener)
                queues(request.name) = QueueMetadata(actor, queueData)
                queueEventListener.foreach(_ ! QueueEvent.QueueCreated(queueData))
                Right(actor)
            }
        }

      case DeleteQueue(queueName) =>
        logger.info(s"Deleting queue $queueName")
        queues.remove(queueName).foreach { case QueueMetadata(actorRef, _) => context.stop(actorRef) }
        queueEventListener.foreach(_ ! QueueEvent.QueueDeleted(queueName))

      case LookupQueue(queueName) =>
        val result = queues.get(queueName).map(_.actorRef)

        logger.debug(s"Looking up queue $queueName, found?: ${result.isDefined}")
        result

      case ListQueues() => queues.keySet.toSeq
    }

  protected def createQueueActor(
      nowProvider: NowProvider,
      queueData: QueueData,
      queueEventListener: Option[ActorRef]
  ): ActorRef = {
    val deadLetterQueueActor = queueData.deadLettersQueue.flatMap { qd => queues.get(qd.name).map(_.actorRef) }
    val copyMessagesToQueueActor = queueData.copyMessagesTo.flatMap { queueName =>
      queues.get(queueName).map(_.actorRef)
    }
    val moveMessagesToQueueActor = queueData.moveMessagesTo.flatMap { queueName =>
      queues.get(queueName).map(_.actorRef)
    }

    context.actorOf(
      Props(
        new QueueActor(
          nowProvider,
          queueData,
          deadLetterQueueActor,
          copyMessagesToQueueActor,
          moveMessagesToQueueActor,
          queueEventListener
        )
      )
    )
  }

  private def sameCreateQueueData(existing: QueueData, requested: CreateQueueData) = {
    !(
      (requested.defaultVisibilityTimeout.isDefined && requested.defaultVisibilityTimeout.get != existing.defaultVisibilityTimeout) ||
        (requested.delay.isDefined && requested.delay.get != existing.delay) ||
        (requested.receiveMessageWait.isDefined && requested.receiveMessageWait.get != existing.receiveMessageWait) ||
        existing.deadLettersQueue != requested.deadLettersQueue ||
        existing.isFifo != requested.isFifo ||
        existing.copyMessagesTo != requested.copyMessagesTo ||
        existing.moveMessagesTo != requested.moveMessagesTo ||
        existing.tags != requested.tags
    )
  }
}
