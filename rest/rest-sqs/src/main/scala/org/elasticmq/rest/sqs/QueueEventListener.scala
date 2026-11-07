package org.elasticmq.rest.sqs

import org.apache.pekko.actor.Actor
import org.elasticmq.actor.queue.{OperationSuccessful, OperationUnsupported, QueueEvent}
import org.elasticmq.util.Logging
import org.elasticmq.{MessageData, QueueData}

import scala.util.control.NonFatal

/** Receives notifications about changes in queues and messages of an embedded server. Register one with
  * [[TheSQSRestServerBuilder.withQueueEventListener]].
  *
  * All methods have empty default implementations, so implement only the ones you need (from Java too, as default
  * interface methods).
  *
  * Callbacks are invoked one at a time, never concurrently, in the order the changes happen. The message callbacks
  * (`messageAdded`, `messageUpdated`, `messageRemoved`) run before the SQS response for the corresponding request is
  * sent, so a slow callback delays responses; the server waits up to 5 seconds. Exceptions thrown by a callback are
  * logged and otherwise ignored.
  */
trait QueueEventListener {
  def queueCreated(queue: QueueData): Unit = ()
  def queueDeleted(queueName: String): Unit = ()
  def queueMetadataUpdated(queue: QueueData): Unit = ()

  def messageAdded(queueName: String, message: MessageData): Unit = ()
  def messageUpdated(queueName: String, message: MessageData): Unit = ()
  def messageRemoved(queueName: String, messageId: String): Unit = ()
}

/** Adapts a [[QueueEventListener]] to the internal `QueueEvent` actor protocol. */
private[sqs] class QueueEventListenerActor(listener: QueueEventListener) extends Actor with Logging {

  def receive: Receive = {
    case QueueEvent.QueueCreated(queue)         => notifyListener(listener.queueCreated(queue))
    case QueueEvent.QueueDeleted(queueName)     => notifyListener(listener.queueDeleted(queueName))
    case QueueEvent.QueueMetadataUpdated(queue) => notifyListener(listener.queueMetadataUpdated(queue))

    case QueueEvent.MessageAdded(queueName, message) =>
      notifyListener(listener.messageAdded(queueName, message.toMessageData))
      sender() ! OperationSuccessful
    case QueueEvent.MessageUpdated(queueName, message) =>
      notifyListener(listener.messageUpdated(queueName, message.toMessageData))
      sender() ! OperationSuccessful
    case QueueEvent.MessageRemoved(queueName, messageId) =>
      notifyListener(listener.messageRemoved(queueName, messageId))
      sender() ! OperationSuccessful

    case QueueEvent.Restore(_) =>
      sender() ! Right(OperationUnsupported)
  }

  private def notifyListener(callback: => Unit): Unit =
    try callback
    catch {
      case NonFatal(e) => logger.error("Queue event listener failed", e)
    }
}
