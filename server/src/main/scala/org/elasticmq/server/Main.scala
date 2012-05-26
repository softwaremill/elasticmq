package org.elasticmq.server

import com.weiglewilczek.slf4s.Logging
import com.twitter.ostrich.admin.RuntimeEnvironment
import java.io.File
import io.Source

object Main extends Logging {
  def main(args: Array[String]) {
    val start = System.currentTimeMillis()
    val version = readVersion()
    logger.info("Starting ElasticMQ server (%s) ...".format(version))

    logUncaughtExceptions()

    val runtime = RuntimeEnvironment(this, Array("-f", configFile, "--config-target", configTarget))
    val server = runtime.loadRuntimeConfig[ElasticMQServer]()
    val shutdown = server.start()

    addShutdownHook(shutdown)

    logger.info("=== ElasticMQ server (%s) started in %d ms ===".format(version, System.currentTimeMillis() - start))
  }

  private def readVersion() = {
    val stream = this.getClass.getResourceAsStream("/version")
    Source.fromInputStream(stream).getLines().next()
  }

  private def logUncaughtExceptions() {
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      def uncaughtException(t: Thread, e: Throwable) {
        logger.error("Uncaught exception in thread: " + t.getName, e)
      }
    })
  }

  private def addShutdownHook(shutdown: () => Unit) {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        logger.info("ElasticMQ server stopping ...")
        shutdown()
        logger.info("=== ElasticMQ server stopped ===")
      }
    });
  }

  private lazy val configFile = Environment.BaseDir + File.separator + "conf" + File.separator + "Default.scala"
  private lazy val configTarget = "tmp"
}
