package org.elasticmq.impl.scheduler

trait VolatileTaskSchedulerModule {
  def volatileTaskScheduler: VolatileTaskScheduler

  trait VolatileTaskScheduler {
    def schedule(block: => Unit)
  }
}