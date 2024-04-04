package org.elasticmq.actor.queue.operations

import org.apache.pekko.actor.ActorRef
import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent}
import org.elasticmq.msg.{MessageMoveTaskFinished, MessageMoveTaskId, MoveFirstMessage, SendMessage}
import org.elasticmq.util.Logging

import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration, NANOSECONDS}

sealed trait MessageMoveTaskState
case object NotMovingMessages extends MessageMoveTaskState
case class MovingMessagesInProgress(numMessagesMoved: Int) extends MessageMoveTaskState

trait MoveMessagesAsyncOps extends Logging {
  this: QueueActorStorage =>

  private var messageMoveTaskState: MessageMoveTaskState = NotMovingMessages

  def startMovingMessages(
      destinationQueue: ActorRef,
      maxNumberOfMessagesPerSecond: Option[Int],
      queueManager: ActorRef
  ): MessageMoveTaskId = {
    val taskId = UUID.randomUUID().toString
    logger.debug("Starting message move task to queue {} (task id: {})", destinationQueue, taskId)
    messageMoveTaskState = MovingMessagesInProgress(0)
    context.self ! MoveFirstMessage(taskId, destinationQueue, maxNumberOfMessagesPerSecond, queueManager)
    taskId
  }

  def moveFirstMessage(
      taskId: MessageMoveTaskId,
      destinationQueue: ActorRef,
      maxNumberOfMessagesPerSecond: Option[Int],
      queueManager: ActorRef
  ): ResultWithEvents[Unit] = {
    messageMoveTaskState match {
      case NotMovingMessages =>
        logger.debug("Moving messages task {} was finished or cancelled", taskId)
        ResultWithEvents.empty
      case MovingMessagesInProgress(numMessagesMovedSoFar) =>
        logger.debug("Trying to move a single message to {} ({} messages left)", destinationQueue, messageQueue.size)
        messageQueue.pop match {
          case Some(internalMessage) =>
            messageMoveTaskState = MovingMessagesInProgress(numMessagesMovedSoFar + 1)
            destinationQueue ! SendMessage(internalMessage.toNewMessageData)
            maxNumberOfMessagesPerSecond match {
              case Some(v) =>
                val nanosInSecond = 1.second.toNanos.toDouble
                val delayNanos = (nanosInSecond / v).toLong
                val delay = FiniteDuration(delayNanos, NANOSECONDS)
                context.system.scheduler.scheduleOnce(
                  delay,
                  context.self,
                  MoveFirstMessage(taskId, destinationQueue, maxNumberOfMessagesPerSecond, queueManager)
                )
              case None =>
                context.self ! MoveFirstMessage(taskId, destinationQueue, maxNumberOfMessagesPerSecond, queueManager)
            }
            ResultWithEvents.onlyEvents(List(QueueEvent.MessageRemoved(queueData.name, internalMessage.id)))
          case None =>
            logger.debug("No more messages to move")
            messageMoveTaskState = NotMovingMessages
            queueManager ! MessageMoveTaskFinished(taskId)
            ResultWithEvents.empty
        }
      }
    }

  def cancelMovingMessages(): Int = {
    val numMessagesMoved = messageMoveTaskState match {
      case NotMovingMessages                          => 0
      case MovingMessagesInProgress(numMessagesMoved) => numMessagesMoved
    }
    messageMoveTaskState = NotMovingMessages
    numMessagesMoved
  }
}
