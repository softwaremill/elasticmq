package org.elasticmq.impl.scheduler

import com.typesafe.scalalogging.slf4j.Logging
import concurrent.{ExecutionContext, Future}
import java.util.concurrent.Executors

trait BackgroundVolatileTaskSchedulerModule extends VolatileTaskSchedulerModule {
  val volatileTaskScheduler = new BackgroundTaskScheduler

  implicit private val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  class BackgroundTaskScheduler extends VolatileTaskScheduler with Logging {
    def schedule(block: => Unit) {
      Future {
        try {
          block
        } catch {
          case e => logger.warn("Failed to execute background task", e)
        }
      }
    }
  }
  
  case class Block(block: () => Unit)
}