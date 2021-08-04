package org.elasticmq.actor.queue

import org.elasticmq.QueueData

sealed trait QueueMetadataChanged

case class QueueCreated(queue: QueueData) extends QueueMetadataChanged
case class QueueDeleted(queueName: String) extends QueueMetadataChanged
case class QueueMetadataUpdated(queue: QueueData) extends QueueMetadataChanged
