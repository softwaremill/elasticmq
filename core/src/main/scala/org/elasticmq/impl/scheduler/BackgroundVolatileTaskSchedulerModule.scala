package org.elasticmq.impl.scheduler

import scala.actors.DaemonActor

trait BackgroundVolatileTaskSchedulerModule extends VolatileTaskSchedulerModule {
  val volatileTaskScheduler = new BackgroundTaskScheduler

  class BackgroundTaskScheduler extends VolatileTaskScheduler {
    object Executor extends DaemonActor {
      def act() {
        loop {
          react {
            case block: (() => Unit) => block()
          }
        }
      }
    }

    Executor.start()

    def schedule(block: => Unit) {
      Executor ! (() => block)
    }
  }
}