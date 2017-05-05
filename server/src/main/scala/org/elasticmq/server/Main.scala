package org.elasticmq.server

import akka.actor.Terminated
import org.elasticmq.util.Logging

import io.Source
import com.typesafe.config.ConfigFactory
import org.elasticmq.server.config.ElasticMQServerConfig

object Main extends Logging {
  def main(args: Array[String]) {
    val start = System.currentTimeMillis()
    val version = readVersion()
    logger.info("Starting ElasticMQ server (%s) ...".format(version))

    logUncaughtExceptions()

    val config = ConfigFactory.load()
    val server = new ElasticMQServer(new ElasticMQServerConfig(config))
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

  private def addShutdownHook(shutdown: () => Terminated) {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        logger.info("ElasticMQ server stopping ...")
        shutdown()
        logger.info("=== ElasticMQ server stopped ===")
      }
    })
  }
}
