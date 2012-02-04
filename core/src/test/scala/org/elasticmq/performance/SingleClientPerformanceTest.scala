package org.elasticmq.performance

import org.elasticmq._

object SingleClientPerformanceTest {
  //val node = NodeBuilder.withInMemoryStorage().build()
  //val node = NodeBuilder.withMySQLStorage("elasticmq", "root", "").build()
  val node = NodeBuilder.withH2InMemoryStorage().build()
  val client = node.nativeClient

  val testQueueName = "twoClientsPerformanceTest"
  val testQueue = client.lookupOrCreateQueue(QueueBuilder(testQueueName)
    .withDefaultVisibilityTimeout(MillisVisibilityTimeout(10000L)))

  def shutdown() {
    node.shutdown()
  }

  def timeWithOpsPerSecond(name: String, block: Int => Int) {
    val start = System.currentTimeMillis()
    val ops = block(0)
    val end = System.currentTimeMillis()

    val seconds = (end - start) / 1000

    println(name+" took: "+seconds)
    if (seconds != 0) println(name+" ops/second: "+(ops/seconds))
    println(name+" ops: "+ops)
  }

  object Receiver {
    def run() {
      def receiveLoop(count: Int): Int = {
        testQueue.receiveMessage(DefaultVisibilityTimeout) match {
          case Some(message) => {
            message.delete()
            receiveLoop(count+1)
          }
          case None => count
        }
      }

      timeWithOpsPerSecond("Receive", receiveLoop _)
    }
  }

  object Sender {
    def run(iterations: Int) {
      timeWithOpsPerSecond("Send", _ => {
        for (i <- 1 to iterations) {
          testQueue.sendMessage("message"+i)
        }

        iterations
      })
    }
  }
}

object SingleClientPerformanceTestSendAndReceive {
  def main(args: Array[String]) {
    SingleClientPerformanceTest.Sender.run(1000)
    SingleClientPerformanceTest.Receiver.run()

    println()
    println("---")
    println()

    SingleClientPerformanceTest.Sender.run(1000)
    SingleClientPerformanceTest.Receiver.run()

    SingleClientPerformanceTest.shutdown()
  }
}