package org.elasticmq.actor.queue

import akka.actor.{ActorContext, ActorRef}
import akka.util.Timeout
import org.elasticmq.util.NowProvider
import org.elasticmq.{FifoDeduplicationIdsHistory, QueueData}

import scala.concurrent.duration.DurationInt

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  var deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]
  def queueMetadataListener: Option[ActorRef]

  def context: ActorContext
  def defaultTimeout: Timeout = 5.seconds

  var queueData: QueueData = initialQueueData
  var messageQueue: MessageQueue = MessageQueue(queueData.isFifo)
  var fifoMessagesHistory: FifoDeduplicationIdsHistory = FifoDeduplicationIdsHistory.newHistory()
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache
}
