package org.elasticmq.actor.queue

import akka.actor.{ActorContext, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import org.elasticmq.util.NowProvider
import org.elasticmq.{FifoDeduplicationIdsHistory, QueueData}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  var deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]
  def queueEventListener: Option[ActorRef]

  def context: ActorContext
  def defaultTimeout: Timeout = 5.seconds

  implicit lazy val ec: ExecutionContext = context.dispatcher
  implicit lazy val timeout: Timeout = defaultTimeout

  var queueData: QueueData = initialQueueData
  var messageQueue: MessageQueue = MessageQueue(queueData.isFifo)
  var fifoMessagesHistory: FifoDeduplicationIdsHistory = FifoDeduplicationIdsHistory.newHistory()
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache

  def sendMessageAddedNotification(internalMessage: InternalMessage): Unit = {
    queueEventListener.foreach(ref => {
      Await.result(ref ? QueueMessageAdded(queueData.name, internalMessage), timeout.duration)
    })
  }

  def sendMessageUpdatedNotification(internalMessage: InternalMessage): Unit = {
    queueEventListener.foreach(ref => {
      Await.result(ref ? QueueMessageUpdated(queueData.name, internalMessage), timeout.duration)
    })
  }

  def sendMessageRemovedNotification(msgId: String): Unit = {
    queueEventListener.foreach(ref => {
      Await.result(ref ? QueueMessageRemoved(queueData.name, msgId), timeout.duration)
    })
  }
}
