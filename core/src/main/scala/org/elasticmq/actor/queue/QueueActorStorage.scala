package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.QueueData
import org.elasticmq.util.NowProvider

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  def deadLettersActorRef: Option[ActorRef]

  var queueData: QueueData = initialQueueData
  var messageQueue = MessageQueue(queueData.isFifo)
  val deadLettersQueueActor: Option[ActorRef] = deadLettersActorRef
}
