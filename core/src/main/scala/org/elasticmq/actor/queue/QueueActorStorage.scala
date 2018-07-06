package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.QueueData
import org.elasticmq.util.NowProvider

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  def deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]

  var queueData: QueueData = initialQueueData
  var messageQueue = MessageQueue(queueData.isFifo)
  var deadLettersQueueActor: Option[ActorRef] = deadLettersActorRef
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache
}
