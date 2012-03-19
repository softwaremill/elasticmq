package org.elasticmq.storage.inmemory

import org.elasticmq.data.QueueData
import org.elasticmq.{MessageId, QueueStatistics, QueueAlreadyExistsException, QueueDoesNotExistException}

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConversions
import org.elasticmq.storage.interfaced.QueuesStorage
import scala.collection.mutable.ConcurrentMap

class InMemoryQueuesStorage(createInMemoryQueue: QueueData => InMemoryQueue) extends QueuesStorage {
  val queues = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, InMemoryQueue])

  def createQueue(queueData: QueueData) {
    if (queues.putIfAbsent(queueData.name, createInMemoryQueue(queueData)) != None) {
      throw new QueueAlreadyExistsException(queueData.name)
    }
  }

  def updateQueue(queueData: QueueData) {
    val queueName = queueData.name
    queues.put(queueName, apply(queueName).copy(queueData = queueData))
  }

  def deleteQueue(queueName: String) {
    queues.remove(queueName)
  }

  def apply(queueName: String): InMemoryQueue = {
    queues.get(queueName).getOrElse(throw new QueueDoesNotExistException(queueName))
  }

  def lookupQueue(queueName: String) = queues.get(queueName).map(_.queueData)

  def listQueues = queues.values.map(_.queueData).toSeq

  def queueStatistics(queueName: String, deliveryTime: Long) = {
    val inMemoryQueue = apply(queueName)
    val messages = inMemoryQueue.messages
    val statistics = inMemoryQueue.statistics

    val (visible, invisible) = messages.messagesById.values.partition(
      message => (message.nextDelivery.get() <= deliveryTime))

    val (delivered, undelivered) = invisible.partition(
      message => statistics.readMessageStatistics(MessageId(message.id)).approximateReceiveCount > 0)

    QueueStatistics(
      visible.size,
      delivered.size,
      undelivered.size)
  }
  
  def replaceWithQueues(newQueues: ConcurrentMap[String, InMemoryQueue]) = {
    queues.clear()
    queues ++= newQueues
  }
}
