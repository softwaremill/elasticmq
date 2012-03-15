package org.elasticmq.performance

import org.elasticmq.{NodeBuilder, Queue}
import org.elasticmq.test._

object MultiThreadPerformanceTest {
  def main(args: Array[String]) {
    val numberOfThreads = 5
    val messageCount = 4000

    //val node = NodeBuilder.withInMemoryStorage().build()
    //val storageName = "InMemory"

    val node = NodeBuilder.withMySQLStorage("elasticmq", "root", "").build()
    val storageName = "MySQL"

    //val node = NodeBuilder.withH2InMemoryStorage().build()
    //val storageName = "H2"
    
    val client = node.nativeClient
    val testQueue = client.lookupOrCreateQueue("perfTest")

    // warm up
    run(storageName, testQueue, 1, 1000)

    run(storageName, testQueue, numberOfThreads, messageCount)
    run(storageName, testQueue, numberOfThreads, messageCount)
    run(storageName, testQueue, numberOfThreads, messageCount)

    node.shutdown()
  }

  def run(storageName: String, queue: Queue, numberOfThreads: Int, messageCount: Int) {
    println("Storage: %s, number of threads: %d, number of messages: %d".format(storageName, numberOfThreads, messageCount))
    val ops = messageCount*numberOfThreads

    val sendTook = timeRunAndJoinThreads(numberOfThreads, () => new SendMessages(queue, messageCount))
    printStats("Send", sendTook, ops)

    val receiveTook = timeRunAndJoinThreads(numberOfThreads, () => new ReceiveMessages(queue, messageCount))
    printStats("Receive", receiveTook, ops)
    assertQueueEmpty(queue)

    println()
  }
  
  def printStats(name: String, took: Long, ops: Int) {
    val seconds = took/1000L
    println("%s took: %d (%d), ops: %d, ops per second: %d".format(name, seconds, took, ops,
      if (seconds == 0) ops else ops/seconds))
  } 
  
  def timeRunAndJoinThreads(numberOfThreads: Int, runnable: () => Runnable) = {
    timed {
      val threads = for (i <- 1 to numberOfThreads) yield {
        val t = new Thread(runnable())
        t.start()
        t
      }

      threads.foreach(_.join())
    }
  }

  class SendMessages(queue: Queue, count: Int) extends Runnable {
    def run() {
      var i = 0;
      while (i < count) {
        queue.sendMessage("message"+i)
        i += 1
      }
    }
  }

  class ReceiveMessages(queue: Queue, count: Int) extends Runnable {
    def run() {
      var i = 0
      while (i < count) {
        val msgOpt = queue.receiveMessage()
        assert(msgOpt != None)
        msgOpt.map(_.delete())
        i += 1
      }
    }
  }
  
  def assertQueueEmpty(queue: Queue) {
    val stats = queue.fetchStatistics()
    assert(stats.approximateNumberOfVisibleMessages == 0)
    assert(stats.approximateNumberOfMessagesDelayed == 0)
    assert(stats.approximateNumberOfInvisibleMessages == 0)
  }
}
