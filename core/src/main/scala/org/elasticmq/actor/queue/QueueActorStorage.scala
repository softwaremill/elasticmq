package org.elasticmq.actor.queue

import akka.actor.{ActorContext, ActorRef}
import org.elasticmq.actor.reply._
import akka.util.Timeout
import org.elasticmq.util.NowProvider
import org.elasticmq.{FifoDeduplicationIdsHistory, QueueData}

import scala.concurrent.{Await, ExecutionContext, Future}
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

  def sendMessageAddedNotification(internalMessage: InternalMessage): Future[OperationStatus] = {
    queueEventListener.map(ref => {
      ref ? QueueMessageAdded(queueData.name, internalMessage)
    }).getOrElse(Future.successful(OperationUnsupported))
  }

  def sendMessageUpdatedNotification(internalMessage: InternalMessage): Future[OperationStatus] = {
    queueEventListener.map(ref => {
      ref ? QueueMessageUpdated(queueData.name, internalMessage)
    }).getOrElse(Future.successful(OperationUnsupported))
  }

  def sendMessageRemovedNotification(msgId: String): Unit = {
    queueEventListener.foreach(ref => {
      Await.result(ref ? QueueMessageRemoved(queueData.name, msgId), timeout.duration)
    })
  }
}
