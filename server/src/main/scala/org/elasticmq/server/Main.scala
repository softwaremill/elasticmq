package org.elasticmq.server

import akka.actor.Terminated
import com.typesafe.config.ConfigFactory
import org.elasticmq.server.config.ElasticMQServerConfig
import org.elasticmq.util.Logging

import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, Future}
import scala.io.Source

object Main extends Logging {
  def main(args: Array[String]): Unit = {
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

  private def logUncaughtExceptions(): Unit = {
    Thread.setDefaultUncaughtExceptionHandler((thread: Thread, ex: Throwable) =>
      logger.error("Uncaught exception in thread: " + thread.getName, ex))
  }

  private def addShutdownHook(shutdown: () => Future[Terminated]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("ElasticMQ server stopping ...")
        Await.result(shutdown(), Inf)
        logger.info("=== ElasticMQ server stopped ===")
      }
    })
  }
}
