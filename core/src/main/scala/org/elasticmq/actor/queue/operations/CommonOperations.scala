package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.NowProvider
import org.elasticmq._

object CommonOperations {

  /** Check whether a new message is a duplicate of the message that's on the queue.
    *
    * @param newMessage      The message that needs to be added to the queue
    * @param queueMessage    The message that's already on the queue
    * @return                Whether the new message counts as a duplicate
    */
  def isDuplicate(newMessage: NewMessageData, queueMessage: InternalMessage, nowProvider: NowProvider): Boolean = {
    lazy val isWithinDeduplicationWindow = queueMessage.created.plusMinutes(5).isAfter(nowProvider.now)
    newMessage.messageDeduplicationId == queueMessage.messageDeduplicationId && isWithinDeduplicationWindow
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
