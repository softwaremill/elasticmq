package org.elasticmq.actor.queue.operations

import org.elasticmq.Limits
import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent, QueueEventWithOperationStatus}
import org.elasticmq.util.Logging

/** Removes messages older than the queue's retention period (`MessageRetentionPeriod`). */
trait ExpireMessagesOps extends Logging {
  this: QueueActorStorage =>

  protected def expireMessages(): List[QueueEventWithOperationStatus] =
    Limits.messageRetentionPeriod(queueData, limits) match {
      case None                  => Nil
      case Some(retentionPeriod) =>
        val expired = messageQueue.removeCreatedAtOrBefore(nowProvider.nowMillis - retentionPeriod.toMillis)
        if (expired.nonEmpty) {
          logger.debug(s"${queueData.name}: Removed ${expired.size} messages older than $retentionPeriod")
        }
        expired.map(message => QueueEvent.MessageRemoved(queueData.name, message.id))
    }
}
