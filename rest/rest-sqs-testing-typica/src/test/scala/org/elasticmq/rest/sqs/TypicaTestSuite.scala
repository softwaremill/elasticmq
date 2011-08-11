package org.elasticmq.rest.sqs

import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.elasticmq.rest.RestServer
import org.elasticmq.{Node, NodeBuilder}
import com.xerox.amazonws.sqs2.{QueueService, SQSUtils}
import org.apache.log4j.BasicConfigurator
import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}

class TypicaTestSuite extends FunSuite with MustMatchers with BeforeAndAfter {
  var node: Node = _
  var server: RestServer = _

  BasicConfigurator.configure();
  InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())

  before {
    node = NodeBuilder.createNode
    server = SQSRestServerFactory.start(node.nativeClient, 8888, "http://localhost:8888")
  }

  after {
    server.stop()
    node.shutdown()
  }

  test("should create a queue") {
    val queueService = newQueueService
    queueService.getOrCreateMessageQueue("testQueue1")
  }

  test("should list created queues") {
    // Given
    val queueService = newQueueService
    queueService.getOrCreateMessageQueue("testQueue1")
    queueService.getOrCreateMessageQueue("testQueue2")

    // When
    val queues = queueService.listMessageQueues(null)

    // Then
    queues.size() must be (2)
    queues.get(0).getUrl.toString must include ("testQueue1")
    queues.get(1).getUrl.toString must include ("testQueue2")
  }

  test("should list queues with the specified prefix") {
    // Given
    val queueService = newQueueService
    queueService.getOrCreateMessageQueue("aaaQueue")
    queueService.getOrCreateMessageQueue("bbbQueue")

    // When
    val queues = queueService.listMessageQueues("aaa")

    // Then
    queues.size() must be (1)
    queues.get(0).getUrl.toString must include ("aaaQueue")
  }

  test("should create and delete a queue") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1")

    // When
    queue.deleteQueue()

    // Then
    newQueueService.listMessageQueues(null).size() must be (0)
  }

  test("should get queue visibility timeout") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1")

    // When
    val vt = queue.getVisibilityTimeout

    // Then
    vt must be (30000)
  }

  test("should set queue visibility timeout") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1")

    // When
    queue.setVisibilityTimeout(10000)

    // Then
    val vt = queue.getVisibilityTimeout
    vt must be (10000)
  }

  test("should send and receive a message") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1")

    // When
    queue.sendMessage("Message 1")
    val message = queue.receiveMessage()

    // Then
    message.getMessageBody must be ("Message 1")
  }

  test("should block message for the visibility timeout duration") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1", 1000)

    // When
    queue.sendMessage("Message 1")
    val m1 = queue.receiveMessage()
    val m2 = queue.receiveMessage()
    Thread.sleep(1100)
    val m3 = queue.receiveMessage()

    // Then
    m1.getMessageBody must be ("Message 1")
    m2 must be (null)
    m3.getMessageBody must be ("Message 1")
  }

  test("should delete a message") {
    // Given
    val queueService = newQueueService
    val queue = queueService.getOrCreateMessageQueue("testQueue1", 500)

    // When
    queue.sendMessage("Message 1")
    val m1 = queue.receiveMessage()
    queue.deleteMessage(m1)
    Thread.sleep(600)
    val m2 = queue.receiveMessage()

    // Then
    m1.getMessageBody must be ("Message 1")
    m2 must be (null)
  }

  def newQueueService = new QueueService("n/a", "n/a", false, "localhost", 8888)
}