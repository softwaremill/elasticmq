package org.elasticmq.rest.sqs

import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.{NodeAddress, NodeBuilder}

object SQSManualTesting {
  def main(args: Array[String]) {
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

    val server = new SQSRestServerBuilder(client).start()
    println("Started")
    readLine()
    server.stop()
    node.shutdown()
    println("Stopped")
  }
}