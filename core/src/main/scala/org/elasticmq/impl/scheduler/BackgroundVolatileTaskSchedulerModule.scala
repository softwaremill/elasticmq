package org.elasticmq.impl.scheduler

import scala.actors.Actor

trait BackgroundVolatileTaskSchedulerModule extends VolatileTaskSchedulerModule {
  val volatileTaskScheduler = new BackgroundTaskScheduler

  class BackgroundTaskScheduler extends VolatileTaskScheduler {
    object Executor extends Actor {
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