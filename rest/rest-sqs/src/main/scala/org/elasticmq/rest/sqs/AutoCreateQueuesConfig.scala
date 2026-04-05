package org.elasticmq.rest.sqs

import org.elasticmq.persistence.CreateQueueMetadata

case class AutoCreateQueuesConfig(enabled: Boolean, template: CreateQueueMetadata)

object AutoCreateQueuesConfig {
  val disabled: AutoCreateQueuesConfig = AutoCreateQueuesConfig(
    enabled = false,
    template = CreateQueueMetadata(name = "")
  )
}

trait AutoCreateQueuesModule {
  def autoCreateQueues: AutoCreateQueuesConfig
}
