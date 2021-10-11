package org.elasticmq.persistence

import org.elasticmq.{CreateQueueDefaults, DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}

case class CreateQueueMetadata(
    name: String,
    defaultVisibilityTimeoutSeconds: Option[Long],
    delaySeconds: Option[Long],
    receiveMessageWaitSeconds: Option[Long],
    deadLettersQueue: Option[DeadLettersQueue],
    isFifo: Boolean,
    hasContentBasedDeduplication: Boolean,
    copyMessagesTo: Option[String] = None,
    moveMessagesTo: Option[String] = None,
    tags: Map[String, String] = Map[String, String]()) {

  def toQueueData(now: DateTime): QueueData = {
    QueueData(
      name = name,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDefaults.DefaultVisibilityTimeout)
      ),
      delay = Duration.standardSeconds(delaySeconds.getOrElse(CreateQueueDefaults.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        receiveMessageWaitSeconds.getOrElse(CreateQueueDefaults.DefaultReceiveMessageWait)
      ),
      created = now,
      lastModified = now,
      deadLettersQueue = deadLettersQueue.map(dlq => DeadLettersQueueData(dlq.name, dlq.maxReceiveCount)),
      isFifo = isFifo,
      hasContentBasedDeduplication = hasContentBasedDeduplication,
      copyMessagesTo = copyMessagesTo,
      moveMessagesTo = moveMessagesTo,
      tags = tags)
  }
}

object CreateQueueMetadata {

  def from(queueData: QueueData): CreateQueueMetadata = {
    CreateQueueMetadata(
      queueData.name,
      Some(queueData.defaultVisibilityTimeout.millis),
      Some(queueData.delay.getStandardSeconds),
      Some(queueData.receiveMessageWait.getStandardSeconds),
      queueData.deadLettersQueue.map(dlq => DeadLettersQueue(dlq.name, dlq.maxReceiveCount)),
      queueData.isFifo,
      queueData.hasContentBasedDeduplication,
      queueData.copyMessagesTo,
      queueData.moveMessagesTo,
      queueData.tags)
  }
}
