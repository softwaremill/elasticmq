package org.elasticmq.actor.queue

import org.elasticmq._
import org.elasticmq.util.NowProvider

import scala.annotation.tailrec
import scala.collection.mutable

trait MessageQueue {

  /** Add a message onto the queue. Note that this doesn't do any deduplication, that should've happened in an earlier
    * step.
    *
    * @param message
    * The message to add onto the queue
    */
  def +=(message: InternalMessage): Unit

  /** Get the messages indexed by their unique id
    *
    * @return
    * The messages indexed by their id
    */
  def byId: Map[String, InternalMessage]

  /** Drop all messages on the queue
    */
  def clear(): Unit

  /** Remove the message with the given id
    *
    * @param messageId
    * The id of the message to remove
    */
  def remove(messageId: String): Unit

  def inMemory: Boolean = true

  def onDelete(): Unit = {}

  /** Return a message queue where all the messages on the queue do not match the given predicate function
    *
    * @param p
    * The predicate function to filter the message by. Any message that does not match the predicate will be retained
    * on the new queue
    * @return
    * The new message queue
    */
  def filterNot(p: InternalMessage => Boolean): MessageQueue

  /** Dequeues `count` messages from the queue
    *
    * @param count
    * The number of messages to dequeue from the queue
    * @param deliveryTime
    * The timestamp from which messages should be available (usually, this is the current millis since epoch. It is
    * useful to pass in a special value during the tests however.)
    * @return
    * The dequeued messages, if any
    */
  def dequeue(count: Int, deliveryTime: Long): List[InternalMessage]

  /** Get the next available message on the given queue
    *
    * @param priorityQueue
    * The queue for which to get the next available message. It's assumed the messages on this queue all belong to the
    * same message group.
    * @param deliveryTime
    * The timestamp from which messages should be available
    * @param accBatch
    * An accumulator holding the messages that have already been retrieved.
    * @param accMessage
    * An accumulator holding the messages that have been dequeued from the priority queue and cannot be delivered.
    * These messages should be put back on the queue before returning to the caller
    * @return
    */
  @tailrec
  protected final def nextVisibleMessage(
                                          priorityQueue: mutable.PriorityQueue[InternalMessage],
                                          deliveryTime: Long,
                                          accBatch: List[InternalMessage],
                                          accMessage: Seq[InternalMessage] = Seq.empty
                                        ): Option[InternalMessage] = {
    if (priorityQueue.nonEmpty) {
      val msg = priorityQueue.dequeue()

      if (byId.get(msg.id).isEmpty) {
        // A message that's not in the byId map is considered to be deleted and can be dropped
        nextVisibleMessage(priorityQueue, deliveryTime, accBatch, accMessage)
      } else {

        lazy val isInBatch = accBatch.exists(_.id == msg.id)
        lazy val isInLocalAcc = accMessage.exists(_.id == msg.id)
        if (msg.deliverable(deliveryTime) && !isInLocalAcc && !isInBatch) {
          // If this message is deliverable, we put all the previously dequeued (but undeliverable) messages back on
          // the queue and return this message for delivery
          priorityQueue ++= accMessage
          Some(msg)
        } else {
          // The message is not deliverable. Put it and all the other previously retrieved messages in this batch back
          // on the priority queue.
          priorityQueue += msg
          priorityQueue ++= accMessage
          None
        }
      }
    } else {
      // If the priority queue is empty, there are no further messages to test. Put any dequeued but unavailable
      // messages back on the queue and return a None
      priorityQueue ++= accMessage
      None
    }
  }
}

object MessageQueue {

  def apply(name: String, persistenceConfig: MessagePersistenceConfig, isFifo: Boolean)(implicit nowProvider: NowProvider): MessageQueue = {
    if (persistenceConfig.enabled) {
      new PersistedMessageQueue(name, persistenceConfig, isFifo)
    } else {
      if (isFifo) {
        new FifoMessageQueue
      } else {
        new SimpleMessageQueue
      }
    }
  }
}
