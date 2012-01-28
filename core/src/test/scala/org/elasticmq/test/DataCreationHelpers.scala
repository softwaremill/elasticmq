package org.elasticmq.test

import org.elasticmq.impl.{MessageData, QueueData}
import org.elasticmq.{MessageId, MillisNextDelivery, MillisVisibilityTimeout}
import org.joda.time.{DateTime, Duration}

trait DataCreationHelpers {
  def createQueueData(name: String, defaultVisibilityTimeout: MillisVisibilityTimeout) =
    QueueData(name, defaultVisibilityTimeout, Duration.ZERO, new DateTime(0), new DateTime(0))

  def createMessageData(id: String, content: String, nextDelivery: MillisNextDelivery) =
    MessageData(MessageId(id), content, nextDelivery, new DateTime(0))
}