package org.elasticmq.impl.scheduler

import com.typesafe.scalalogging.slf4j.Logging
import actors.DaemonActor

trait BackgroundVolatileTaskSchedulerModule extends VolatileTaskSchedulerModule {
  val volatileTaskScheduler = new BackgroundTaskScheduler

  class BackgroundTaskScheduler extends VolatileTaskScheduler with Logging {
    object Executor extends DaemonActor {
      def act() {
        loop {
          react {
            case Block(block) => {
              try {
                block()
              } catch {
                case e => logger.warn("Failed to execute background task", e)
              }
            }
          }
        }
      }
    }

    Executor.start()

    def schedule(block: => Unit) {
      Executor ! Block(() => block)
    }
  }
  
  case class Block(block: () => Unit)
}