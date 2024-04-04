package org.elasticmq.actor.queue.operations

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.ask
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage, QueueEvent}
import org.elasticmq.msg.{MoveFirstMessageToQueue, SendMessage, StartMessageMoveTaskId}
import org.elasticmq.util.Logging
import org.elasticmq.{DeduplicationId, MoveDestination, MoveToDLQ}

import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration, NANOSECONDS}

trait MoveMessagesAsyncOps extends Logging {
  this: QueueActorStorage =>

  def startMovingMessages(
      destinationQueue: ActorRef,
      maxNumberOfMessagesPerSecond: Option[Int]
  ): StartMessageMoveTaskId = {
    val taskId = UUID.randomUUID().toString
    logger.debug("Starting message move task to queue {} (task id: {})", destinationQueue, taskId)
    context.self ! MoveFirstMessageToQueue(destinationQueue, maxNumberOfMessagesPerSecond)
    taskId
  }

  def moveFirstMessage(
      destinationQueue: ActorRef,
      maxNumberOfMessagesPerSecond: Option[Int]
  ): ResultWithEvents[Unit] = {
    logger.debug("Trying to move a single message to {} ({} messages left)", destinationQueue, messageQueue.size)
    messageQueue.pop match {
      case Some(internalMessage) =>
        destinationQueue ! SendMessage(internalMessage.toNewMessageData)
        maxNumberOfMessagesPerSecond match {
          case Some(v) =>
            val nanosInSecond = 1.second.toNanos.toDouble
            val delayNanos = (nanosInSecond / v).toLong
            val delay = FiniteDuration(delayNanos, NANOSECONDS)
            context.system.scheduler.scheduleOnce(
              delay,
              context.self,
              MoveFirstMessageToQueue(destinationQueue, maxNumberOfMessagesPerSecond)
            )
          case None =>
            context.self ! MoveFirstMessageToQueue(destinationQueue, maxNumberOfMessagesPerSecond)
        }
        ResultWithEvents.onlyEvents(List(QueueEvent.MessageRemoved(queueData.name, internalMessage.id)))
      case None =>
        ResultWithEvents.empty
    }
  }
}
