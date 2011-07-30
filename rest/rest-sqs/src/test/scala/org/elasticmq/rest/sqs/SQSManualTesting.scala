package org.elasticmq.rest.sqs

import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}
import org.apache.log4j.{BasicConfigurator}
import org.elasticmq.NodeBuilder

object SQSManualTesting {
  def main(args: Array[String]) {
    BasicConfigurator.configure();
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())

    val node = NodeBuilder.createNode
    val client = node.nativeClient

    val server = new SQSRestServerFactory(client).start(8888)
    println("Started")
    readLine()
    server.stop()
    node.shutdown()
    println("Stopped")
  }
}