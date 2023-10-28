package org.elasticmq.persistence

import org.elasticmq.util.OffsetDateTimeUtil
import org.elasticmq.{CreateQueueData, DeadLettersQueueData, MillisVisibilityTimeout, QueueData}

import java.time.Duration

case class CreateQueueMetadata(
    name: String,
    defaultVisibilityTimeoutSeconds: Option[Long] = None,
    delaySeconds: Option[Long] = None,
    receiveMessageWaitSeconds: Option[Long] = None,
    created: Long = 0L,
    lastModified: Long = 0L,
    deadLettersQueue: Option[DeadLettersQueue] = None,
    isFifo: Boolean = false,
    hasContentBasedDeduplication: Boolean = false,
    copyMessagesTo: Option[String] = None,
    moveMessagesTo: Option[String] = None,
    tags: Map[String, String] = Map[String, String]()
) {

  def toCreateQueueData: CreateQueueData = {
    CreateQueueData(
      name = name,
      defaultVisibilityTimeoutSeconds.map(sec => MillisVisibilityTimeout.fromSeconds(sec)),
      delaySeconds.map(sec => Duration.ofSeconds(sec)),
      receiveMessageWaitSeconds.map(sec => Duration.ofSeconds(sec)),
      created = Some(OffsetDateTimeUtil.ofEpochMilli(created)),
      lastModified = Some(OffsetDateTimeUtil.ofEpochMilli(lastModified)),
      deadLettersQueue = deadLettersQueue.map(dlq => DeadLettersQueueData(dlq.name, dlq.maxReceiveCount)),
      isFifo = isFifo,
      hasContentBasedDeduplication = hasContentBasedDeduplication,
      copyMessagesTo = copyMessagesTo,
      moveMessagesTo = moveMessagesTo,
      tags = tags
    )
  }
}

object CreateQueueMetadata {

  def from(queueData: QueueData): CreateQueueMetadata = {
    CreateQueueMetadata(
      queueData.name,
      Some(queueData.defaultVisibilityTimeout.seconds),
      Some(queueData.delay.getSeconds),
      Some(queueData.receiveMessageWait.getSeconds),
      queueData.created.toInstant.toEpochMilli,
      queueData.lastModified.toInstant.toEpochMilli,
      queueData.deadLettersQueue.map(dlq => DeadLettersQueue(dlq.name, dlq.maxReceiveCount)),
      queueData.isFifo,
      queueData.hasContentBasedDeduplication,
      queueData.copyMessagesTo,
      queueData.moveMessagesTo,
      queueData.tags
    )
  }

  def mergePersistedAndBaseQueues(
      persistedQueues: List[CreateQueueMetadata],
      baseQueues: List[CreateQueueMetadata]
  ): List[CreateQueueMetadata] = {
    val persistedQueuesName = persistedQueues.map(_.name).toSet
    val result = persistedQueues ++ baseQueues.filterNot(queue => persistedQueuesName.contains(queue.name))
    QueueSorter.sortCreateQueues(result)
  }
}
