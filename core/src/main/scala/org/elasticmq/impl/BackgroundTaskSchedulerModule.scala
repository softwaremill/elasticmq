package org.elasticmq.impl

import scala.actors.Actor

trait BackgroundTaskSchedulerModule {
  val backgroundTaskScheduler = new BackgroundTaskScheduler

  class BackgroundTaskScheduler {
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

    def schedule(block: () => Unit) {
      Executor ! block
    }
  }
}