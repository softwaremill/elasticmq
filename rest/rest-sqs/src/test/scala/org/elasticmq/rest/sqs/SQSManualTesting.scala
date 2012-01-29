package org.elasticmq.rest.sqs

import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}
import org.elasticmq.NodeBuilder

object SQSManualTesting {
  def main(args: Array[String]) {
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())
    mysql()
  }

  def mysql() {
    val node = NodeBuilder.withMySQLStorage("elasticmq", "root", "").build()
    val client = node.nativeClient

    readLine()

    node.shutdown()
  }

  def simple() {
    val node = NodeBuilder.withInMemoryStorage().build()
    val client = node.nativeClient

    val server = SQSRestServerFactory.start(client, 8888, "http://localhost:8888")
    println("Started")
    readLine()
    server.stop()
    node.shutdown()
    println("Stopped")
  }
}