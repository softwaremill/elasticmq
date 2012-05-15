package org.elasticmq.server

import com.weiglewilczek.slf4s.Logging
import com.twitter.ostrich.admin.RuntimeEnvironment
import java.io.File

object Main extends Logging {
  def main(args: Array[String]) {
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      def uncaughtException(t: Thread, e: Throwable) {
        logger.error("Uncaught exception in thread: " + t.getName, e)
      }
    })

    val runtime = RuntimeEnvironment(this, Array("-f", configFile, "--config-target", configTarget))
    val server = runtime.loadRuntimeConfig[ElasticMQServer]()
    server.start()
  }

  private lazy val configFile = Environment.BaseDir + File.separator + "conf" + File.separator + "Default.scala"
  private lazy val configTarget = "tmp"
}
