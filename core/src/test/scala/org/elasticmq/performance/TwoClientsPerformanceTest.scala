package org.elasticmq.performance

import org.elasticmq._

object TwoClientsPerformanceTest {
  // Slows down, useful for debugging:
  // org.apache.log4j.BasicConfigurator.configure();

  val node = NodeBuilder.withMySQLStorage("elasticmq", "root", "").build()
  val client = node.nativeClient

  val testQueueName = "twoClientsPerformanceTest"
  val testQueue = {
    client.queueClient.lookupQueue(testQueueName) match {
      case Some(queue) => queue
      case None => client.queueClient.createQueue(Queue(testQueueName, MillisVisibilityTimeout(10000)))
    }
  }

  def shutdown() {
    node.shutdown()
  }

  object Receiver {
    def run() {
      def receiveLoop(count: Int): Int = {
        client.messageClient.receiveMessage(testQueue) match {
          case Some(message) => {
            client.messageClient.deleteMessage(message)
            receiveLoop(count+1)
          }
          case None => count
        }
      }

      val start = System.currentTimeMillis()
      val received = receiveLoop(0)
      val end = System.currentTimeMillis()

      val seconds = (end - start) / 1000

      println("Took: "+seconds)
      println("Msgs/second: "+(received/seconds))
      println("Received messages: "+received)

      shutdown()
    }
  }

  object Sender {
    def run() {
      for (i <- 1 to 1000) {
        client.messageClient.sendMessage(Message(testQueue, "message"+i))
      }

      shutdown()
    }
  }
}

object TwoClientsPerformanceTestReceiver {
  def main(args: Array[String]) {
    println("Press any key to start ...")
    readLine()
    TwoClientsPerformanceTest.Receiver.run()
  }
}

object TwoClientsPerformanceTestSender {
  def main(args: Array[String]) {
    TwoClientsPerformanceTest.Sender.run()
  }
}

object TwoClientsPerformanceTestSendAndReceive {
  def main(args: Array[String]) {
    TwoClientsPerformanceTest.Sender.run()
    TwoClientsPerformanceTest.Receiver.run()
  }
}