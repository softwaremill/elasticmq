package org.elasticmq.rest.sqs

import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}
import org.elasticmq.NodeBuilder
import org.elasticmq.storage.squeryl.{DBConfiguration, SquerylStorage}
import org.elasticmq.storage.inmemory.InMemoryStorage

object SQSManualTesting {
  def main(args: Array[String]) {
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())
    mysql()
  }

  def mysql() {
    val node = NodeBuilder.withStorage(
      new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "")))
    val client = node.nativeClient

    readLine()

    node.shutdown()
  }

  def simple() {
    val node = NodeBuilder.withStorage(new InMemoryStorage)
    val client = node.nativeClient

    val server = SQSRestServerFactory.start(client, 8888, "http://localhost:8888")
    println("Started")
    readLine()
    server.stop()
    node.shutdown()
    println("Stopped")
  }
}