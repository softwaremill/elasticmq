package org.elasticmq.actor.queue.operations

import org.apache.pekko.actor.ActorRef
import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent}
import org.elasticmq.msg.{MessageMoveTaskFinished, MessageMoveTaskId, MoveFirstMessage, SendMessage}
import org.elasticmq.util.Logging
import org.elasticmq.{ElasticMQError, MessageMoveTaskAlreadyRunning}

import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration, NANOSECONDS}

sealed trait MessageMoveTaskState
case object NotMovingMessages extends MessageMoveTaskState
case class MovingMessagesInProgress(
    numberOfMessagesMoved: Long,
    numberOfMessagesToMove: Long,
    destinationArn: Option[String],
    maxNumberOfMessagesPerSecond: Option[Int],
    sourceArn: String,
    startedTimestamp: Long,
    taskHandle: MessageMoveTaskId
) extends MessageMoveTaskState

case class MessageMoveTaskData(
    numberOfMessagesMoved: Long,
    numberOfMessagesToMove: Long,
    destinationArn: Option[String],
    maxNumberOfMessagesPerSecond: Option[Int],
    sourceArn: String,
    startedTimestamp: Long,
    status: String, // RUNNING, COMPLETED, CANCELLING, CANCELLED, and FAILED
    taskHandle: MessageMoveTaskId
)

trait MoveMessagesAsyncOps extends Logging {
  this: QueueActorStorage =>

  private val prevMessageMoveTasks = collection.mutable.Buffer[MessageMoveTaskData]()
  private var messageMoveTaskState: MessageMoveTaskState = NotMovingMessages

  def startMovingMessages(
      destinationQueue: ActorRef,
      destinationArn: Option[String],
      sourceArn: String,
      maxNumberOfMessagesPerSecond: Option[Int],
      queueManager: ActorRef
  ): Either[ElasticMQError, MessageMoveTaskId] = {
    messageMoveTaskState match {
      case NotMovingMessages =>
        val taskHandle = UUID.randomUUID().toString
        logger.debug("Starting message move task to queue {} (task handle: {})", destinationQueue, taskHandle)
        messageMoveTaskState = MovingMessagesInProgress(
          0,
          messageQueue.size,
          destinationArn,
          maxNumberOfMessagesPerSecond,
          sourceArn,
          startedTimestamp = System.currentTimeMillis(),
          taskHandle
        )
        context.self ! MoveFirstMessage(destinationQueue, queueManager)
        Right(taskHandle)
      case _: MovingMessagesInProgress =>
        Left(new MessageMoveTaskAlreadyRunning(queueData.name))
    }
  }

  def moveFirstMessage(
      destinationQueue: ActorRef,
      queueManager: ActorRef
  ): ResultWithEvents[Unit] = {
    messageMoveTaskState match {
      case NotMovingMessages =>
        logger.debug("Not moving messages")
        ResultWithEvents.empty
      case mmInProgress @ MovingMessagesInProgress(
            numberOfMessagesMoved,
            _,
            destinationArn,
            maxNumberOfMessagesPerSecond,
            sourceArn,
            startedTimestamp,
            taskHandle
          ) =>
        logger.debug("Trying to move a single message to {} ({} messages left)", destinationQueue, messageQueue.size)
        messageQueue.pop match {
          case Some(internalMessage) =>
            messageMoveTaskState = mmInProgress.copy(numberOfMessagesMoved = numberOfMessagesMoved + 1)
            destinationQueue ! SendMessage(internalMessage.toNewMessageData)
            maxNumberOfMessagesPerSecond match {
              case Some(v) =>
                val nanosInSecond = 1.second.toNanos.toDouble
                val delayNanos = (nanosInSecond / v).toLong
                val delay = FiniteDuration(delayNanos, NANOSECONDS)
                context.system.scheduler.scheduleOnce(
                  delay,
                  context.self,
                  MoveFirstMessage(destinationQueue, queueManager)
                )
              case None =>
                context.self ! MoveFirstMessage(destinationQueue, queueManager)
            }
            ResultWithEvents.onlyEvents(List(QueueEvent.MessageRemoved(queueData.name, internalMessage.id)))
          case None =>
            logger.debug("No more messages to move")
            prevMessageMoveTasks += MessageMoveTaskData(
              numberOfMessagesMoved,
              numberOfMessagesToMove = 0,
              destinationArn,
              maxNumberOfMessagesPerSecond,
              sourceArn,
              startedTimestamp,
              status = "COMPLETED",
              taskHandle
            )
            messageMoveTaskState = NotMovingMessages
            queueManager ! MessageMoveTaskFinished(taskHandle)
            ResultWithEvents.empty
        }
    }
  }

  def cancelMovingMessages(): Long = {
    val numMessagesMoved = messageMoveTaskState match {
      case NotMovingMessages                      => 0
      case mmInProgress: MovingMessagesInProgress => mmInProgress.numberOfMessagesMoved
    }
    messageMoveTaskState = NotMovingMessages
    numMessagesMoved
  }

  def getMovingMessagesTasks: List[MessageMoveTaskData] = {
    val runningTaskAsList = messageMoveTaskState match {
      case NotMovingMessages => List.empty
      case MovingMessagesInProgress(
            numberOfMessagesMoved,
            numberOfMessagesToMove,
            destinationArn,
            maxNumberOfMessagesPerSecond,
            sourceArn,
            startedTimestamp,
            taskHandle
          ) =>
        List(
          MessageMoveTaskData(
            numberOfMessagesMoved,
            numberOfMessagesToMove,
            destinationArn,
            maxNumberOfMessagesPerSecond,
            sourceArn,
            startedTimestamp,
            status = "RUNNING",
            taskHandle
          )
        )
    }
    (prevMessageMoveTasks.toList ++ runningTaskAsList).reverse
  }
}
