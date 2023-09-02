package org.elasticmq

import java.time.{Duration, OffsetDateTime}

case class CreateQueueData(
    name: String,
    defaultVisibilityTimeout: Option[MillisVisibilityTimeout] = None,
    delay: Option[Duration] = None,
    receiveMessageWait: Option[Duration] = None,
    created: Option[OffsetDateTime] = None,
    lastModified: Option[OffsetDateTime] = None,
    deadLettersQueue: Option[DeadLettersQueueData] = None,
    isFifo: Boolean = false,
    hasContentBasedDeduplication: Boolean = false,
    copyMessagesTo: Option[String] = None,
    moveMessagesTo: Option[String] = None,
    tags: Map[String, String] = Map[String, String]()
) {
  def toQueueData: QueueData = {
    val now = OffsetDateTime.now()
    QueueData(
      name,
      defaultVisibilityTimeout.getOrElse(
        MillisVisibilityTimeout.fromSeconds(CreateQueueDefaults.DefaultVisibilityTimeout)
      ),
      delay.getOrElse(Duration.ofSeconds(CreateQueueDefaults.DefaultDelay)),
      receiveMessageWait.getOrElse(Duration.ofSeconds(CreateQueueDefaults.DefaultReceiveMessageWait)),
      created.getOrElse(now),
      lastModified.getOrElse(now),
      deadLettersQueue,
      isFifo,
      hasContentBasedDeduplication,
      copyMessagesTo,
      moveMessagesTo,
      tags
    )
  }
}

object CreateQueueData {
  def from(queueData: QueueData): CreateQueueData =
    CreateQueueData(
      queueData.name,
      Some(queueData.defaultVisibilityTimeout),
      Some(queueData.delay),
      Some(queueData.receiveMessageWait),
      Some(queueData.created),
      Some(queueData.lastModified),
      queueData.deadLettersQueue,
      queueData.isFifo,
      queueData.hasContentBasedDeduplication,
      queueData.copyMessagesTo,
      queueData.moveMessagesTo,
      queueData.tags
    )
}
