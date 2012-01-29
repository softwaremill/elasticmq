package org.elasticmq.storage.inmemory

import org.joda.time.DateTime
import org.elasticmq._
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
import org.elasticmq.impl.{QueueData, MessageData}
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions

trait InMemoryStorageModelModule {
  this: InMemoryMessageStorageModule =>

  type StatisticsStorage = ConcurrentMap[MessageId, MessageStatistics]

  case class InMemoryQueue(queueData: QueueData,
                           messageStorage: OneQueueInMemoryMessageStorage,
                           statisticStorage: StatisticsStorage)
  
  object InMemoryQueue {
    def apply(queueData: QueueData) = new InMemoryQueue(queueData,
      new OneQueueInMemoryMessageStorage(queueData.name),
      JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[MessageId, MessageStatistics]))
  }
  
  case class InMemoryMessage(id: String, nextDelivery: AtomicLong, content: String, created: DateTime,
                             nextDeliveryState: AtomicReference[MessageNextDeliveryState])
    extends Comparable[InMemoryMessage] {

    def compareTo(other: InMemoryMessage) = nextDelivery.get().compareTo(other.nextDelivery.get())

    def toMessageData = MessageData(MessageId(id), content, MillisNextDelivery(nextDelivery.get()), created)
  }
  
  object InMemoryMessage {
    def from(message: MessageData) = InMemoryMessage(
      message.id.id,
      new AtomicLong(message.nextDelivery.millis),
      message.content,
      message.created,
      new AtomicReference(NextDeliveryUnchanged))
  }

  sealed abstract class MessageNextDeliveryState

  case object NextDeliveryUnchanged extends MessageNextDeliveryState

  // The message's next delivery is being updated. The message should be re-inserted into the queue. This may cause
  // multiple tries to receive the message and put it back (while the next delivery is updated). So in fact this is
  // an active lock.
  case object NextDeliveryIsBeingUpdated extends MessageNextDeliveryState

  // The message's next delivery has been updated. When received, it must be re-inserted to the right position
  // in the queue.
  case object NextDeliveryUpdated extends MessageNextDeliveryState
}