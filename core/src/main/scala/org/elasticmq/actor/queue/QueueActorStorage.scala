package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.QueueData
import org.elasticmq.util.NowProvider

import scala.collection.mutable

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  var deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]

  var queueData: QueueData = initialQueueData
  var messageQueue = MessageQueue(queueData.isFifo)
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache
  val inflightMessageIds = new mutable.TreeSet[String]()
}
