package org.elasticmq.actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CancelMovingMessages, GetQueueData, MessageMoveTaskHandle, StartMovingMessages}
import org.elasticmq.util.Logging
import org.elasticmq.{ElasticMQError, InvalidMessageMoveTaskHandle}

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait QueueManagerMessageMoveOps extends Logging {
  this: QueueManagerActorStorage =>

  private val messageMoveTasks = mutable.HashMap[MessageMoveTaskHandle, ActorRef]()

  def startMessageMoveTask(
      sourceQueue: ActorRef,
      sourceArn: String,
      destinationQueue: Option[ActorRef],
      destinationArn: Option[String],
      maxNumberOfMessagesPerSecond: Option[Int]
  )(implicit timeout: Timeout): ReplyAction[Either[ElasticMQError, MessageMoveTaskHandle]] = {
    val self = context.self
    val replyTo = context.sender()
    (for {
      destinationQueueActorRef <- destinationQueue
        .map(Future.successful)
        .getOrElse(findDeadLetterQueueSource(sourceQueue))
      result <- sourceQueue ? StartMovingMessages(
        destinationQueueActorRef,
        destinationArn,
        sourceArn,
        maxNumberOfMessagesPerSecond,
        self
      )
    } yield (result, destinationQueueActorRef)).onComplete {
      case Success((result, destinationQueueActorRef)) =>
        result match {
          case Right(taskHandle) =>
            logger.debug("Message move task {} => {} created", sourceQueue, destinationQueueActorRef)
            messageMoveTasks.put(taskHandle, sourceQueue)
            replyTo ! Right(taskHandle)
          case Left(error) =>
            logger.error("Failed to start message move task: {}", error)
            replyTo ! Left(error)
        }
      case Failure(ex) => logger.error("Failed to start message move task", ex)
    }
    DoNotReply()
  }

  def onMessageMoveTaskFinished(taskHandle: MessageMoveTaskHandle): ReplyAction[Unit] = {
    logger.debug("Message move task {} finished", taskHandle)
    messageMoveTasks.remove(taskHandle)
    DoNotReply()
  }

  def cancelMessageMoveTask(taskHandle: MessageMoveTaskHandle): ReplyAction[Either[ElasticMQError, Long]] = {
    logger.info("Cancelling message move task {}", taskHandle)
    messageMoveTasks.get(taskHandle) match {
      case Some(sourceQueue) =>
        val replyTo = context.sender()
        sourceQueue ? CancelMovingMessages() onComplete {
          case Success(numMessageMoved) =>
            logger.debug("Message move task {} cancelled", taskHandle)
            messageMoveTasks.remove(taskHandle)
            replyTo ! Right(numMessageMoved)
          case Failure(ex) =>
            logger.error("Failed to cancel message move task", ex)
            replyTo ! Left(ex)
        }
        DoNotReply()
      case None =>
        ReplyWith(Left(new InvalidMessageMoveTaskHandle(taskHandle)))
    }
  }

  private def findDeadLetterQueueSource(sourceQueue: ActorRef) = {
    val queueDataF = sourceQueue ? GetQueueData()
    queueDataF.map { queueData =>
      queues
        .filter { case (_, data) =>
          data.queueData.deadLettersQueue.exists(dlqData => dlqData.name == queueData.name)
        }
        .head
        ._2
        .actorRef
    }
  }
}
