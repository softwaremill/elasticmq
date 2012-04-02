package org.elasticmq.rest.sqs

import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.{NodeAddress, NodeBuilder}

object SQSManualTesting {
  def main(args: Array[String]) {
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())
    mysql()
  }

  def mysql() {
//    val node = NodeBuilder.withStorage(
//      new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "")))
    val node = NodeBuilder.withStorage(new InMemoryStorage)
    val client = node.nativeClient

    readLine()

    node.shutdown()
  }

  def simple() {
    val node = NodeBuilder.withStorage(new InMemoryStorage)
    val client = node.nativeClient

    val server = SQSRestServerFactory.start(client, 8888, NodeAddress("http://localhost:8888"))
    println("Started")
    readLine()
    server.stop()
    node.shutdown()
    println("Stopped")
  }
}