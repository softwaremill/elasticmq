package org.elasticmq.rest.sqs.integration

import org.elasticmq.rest.sqs.{QueueEventListener, SQSRestServerBuilder}
import org.elasticmq.{MessageData, QueueData}
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.services.sqs.{SqsClient => AwsSqsClient}

import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._
import scala.util.Try

class QueueEventListenerTests extends AnyFunSuite with Matchers with Eventually {

  private class RecordingListener extends QueueEventListener {
    val events = new ConcurrentLinkedQueue[String]()

    override def queueCreated(queue: QueueData): Unit = events.add(s"queueCreated:${queue.name}")
    override def queueDeleted(queueName: String): Unit = events.add(s"queueDeleted:$queueName")
    override def queueMetadataUpdated(queue: QueueData): Unit =
      events.add(s"queueMetadataUpdated:${queue.name}:${queue.defaultVisibilityTimeout.seconds}")

    override def messageAdded(queueName: String, message: MessageData): Unit =
      events.add(s"messageAdded:$queueName:${message.content.value}")
    override def messageUpdated(queueName: String, message: MessageData): Unit =
      events.add(s"messageUpdated:$queueName:${message.content.value}:${message.statistics.approximateReceiveCount}")
    override def messageRemoved(queueName: String, messageId: String): Unit =
      events.add(s"messageRemoved:$queueName")

    def recorded: List[String] = events.asScala.toList
  }

  private def withServer(listener: QueueEventListener)(test: AwsSqsClient => Unit): Unit = {
    val server = SQSRestServerBuilder.withDynamicPort().withQueueEventListener(listener).start()
    val port = server.waitUntilStarted().localAddress.getPort

    val client = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI(s"http://localhost:$port"))
      .build()

    try test(client)
    finally {
      Try(client.close())
      Try(server.stopAndWait())
    }
  }

  test("should notify the listener about queue and message changes") {
    val listener = new RecordingListener
    withServer(listener) { client =>
      val queueUrl = client.createQueue(CreateQueueRequest.builder().queueName("q1").build()).queueUrl()
      eventually(listener.recorded shouldBe List("queueCreated:q1"))

      client.setQueueAttributes(
        SetQueueAttributesRequest
          .builder()
          .queueUrl(queueUrl)
          .attributes(Map(QueueAttributeName.VISIBILITY_TIMEOUT -> "15").asJava)
          .build()
      )
      eventually(listener.recorded should contain("queueMetadataUpdated:q1:15"))

      // message callbacks complete before the SQS response is sent, so no need to wait for them
      client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("hello").build())
      listener.recorded.last shouldBe "messageAdded:q1:hello"

      val received = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).build()).messages()
      listener.recorded.last shouldBe "messageUpdated:q1:hello:1"

      client.deleteMessage(
        DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(received.get(0).receiptHandle()).build()
      )
      listener.recorded.last shouldBe "messageRemoved:q1"

      client.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build())
      eventually(listener.recorded.last shouldBe "queueDeleted:q1")
    }
  }

  test("should keep serving requests when the listener throws") {
    val failingListener = new QueueEventListener {
      override def queueCreated(queue: QueueData): Unit = throw new RuntimeException("boom")
      override def messageAdded(queueName: String, message: MessageData): Unit = throw new RuntimeException("boom")
    }
    withServer(failingListener) { client =>
      val queueUrl = client.createQueue(CreateQueueRequest.builder().queueName("q2").build()).queueUrl()
      client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("hello").build())

      val received = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).build()).messages()
      received.asScala.map(_.body()) shouldBe List("hello")
    }
  }
}
