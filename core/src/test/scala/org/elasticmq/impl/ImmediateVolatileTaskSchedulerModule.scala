package org.elasticmq.impl

import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule

trait ImmediateVolatileTaskSchedulerModule extends VolatileTaskSchedulerModule {
  val volatileTaskScheduler = new VolatileTaskScheduler {
    def schedule(block: => Unit) {
      block
    }
  }
}