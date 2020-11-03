package org.elasticmq.actor.queue.operations

import org.elasticmq._
import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.NowProvider

object CommonOperations {

  def wasRegistered(
      newMessage: NewMessageData,
      deduplicationIdsHistory: FifoDeduplicationIdsHistory
  ): Option[InternalMessage] = {
    deduplicationIdsHistory.wasRegistered(newMessage.messageDeduplicationId)
  }

  def computeNextDelivery(
      visibilityTimeout: VisibilityTimeout,
      queueData: QueueData,
      nowProvider: NowProvider
  ): MillisNextDelivery = {
    val nextDeliveryDelta = getVisibilityTimeoutMillis(visibilityTimeout, queueData)
    MillisNextDelivery(nowProvider.nowMillis + nextDeliveryDelta)
  }

  private def getVisibilityTimeoutMillis(visibilityTimeout: VisibilityTimeout, queueData: QueueData) =
    visibilityTimeout match {
      case DefaultVisibilityTimeout        => queueData.defaultVisibilityTimeout.millis
      case MillisVisibilityTimeout(millis) => millis
    }

}
