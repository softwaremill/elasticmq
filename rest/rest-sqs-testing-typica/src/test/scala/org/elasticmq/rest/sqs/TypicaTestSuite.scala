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
    server = new SQSRestServerFactory(node.nativeClient).start(8888)
  }

  after {
    server.stop()
    node.shutdown()
  }

  test("should create a queue") {
    val queueService = new QueueService("n/a", "n/a", false, "localhost", 8888)
    queueService.getOrCreateMessageQueue("testQueue1")
  }
}