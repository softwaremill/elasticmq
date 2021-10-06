package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.util.NowProvider
import org.elasticmq.{FifoDeduplicationIdsHistory, MessagePersistenceConfig, QueueData}

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData
  def persistenceConfig: MessagePersistenceConfig

  var deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]

  var queueData: QueueData = initialQueueData
  var messageQueue: MessageQueue = MessageQueue(queueData.name, persistenceConfig, queueData.isFifo)(nowProvider)
  var fifoMessagesHistory: FifoDeduplicationIdsHistory = FifoDeduplicationIdsHistory.newHistory()
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache
}
