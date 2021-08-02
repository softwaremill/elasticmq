package org.elasticmq.actor.queue

import org.elasticmq.QueueData

case class PersistQueue(queue: QueueData)
case class RemoveQueue(queueName: String)
case class UpdateQueueMetadata(queue: QueueData)
