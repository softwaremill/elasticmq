package org.elasticmq.rest.sqs

import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.elasticmq.rest.RestServer
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model._
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.{Node, NodeBuilder}
import scala.util.control.Exception._
import com.amazonaws.AmazonServiceException

class AmazonJavaSdkTestSuite extends FunSuite with MustMatchers with BeforeAndAfter {
  val visibilityTimeoutAttribute = "VisibilityTimeout"
  val defaultVisibilityTimeoutAttribute = "VisibilityTimeout"
  val delaySecondsAttribute = "DelaySeconds"

  var node: Node = _

  var strictServer: RestServer = _
  var relaxedServer: RestServer = _

  var client: AmazonSQS = _ // strict server
  var relaxedClient: AmazonSQS = _

  before {
    node = NodeBuilder.withStorage(new InMemoryStorage)
    strictServer = new SQSRestServerBuilder(node.nativeClient).start()
    relaxedServer = new SQSRestServerBuilder(node.nativeClient).withPort(9325).withSQSLimits(SQSLimits.Relaxed).start()

    client = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    client.setEndpoint("http://localhost:9324")

    relaxedClient = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    relaxedClient.setEndpoint("http://localhost:9325")
  }

  after {
    client.shutdown()

    strictServer.stop()
    relaxedServer.stop()

    node.shutdown()
  }

  test("should create a queue") {
    client.createQueue(new CreateQueueRequest("testQueue1"))
  }

  test("should get queue url") {
    // Given
    client.createQueue(new CreateQueueRequest("testQueue1"))

    // When
    val queueUrl = client.getQueueUrl(new GetQueueUrlRequest("testQueue1")).getQueueUrl

    // Then
    queueUrl must include ("testQueue1")
  }

  test("should create a queue with the specified visibilty timeout") {
    // When
    client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14")))

    // Then
    val queueUrls = client.listQueues().getQueueUrls

    queueUrls.size() must be (1)

    queueVisibilityTimeout(queueUrls.get(0)) must be (14)
  }

  test("should list created queues") {
    // Given
    client.createQueue(new CreateQueueRequest("testQueue1"))
    client.createQueue(new CreateQueueRequest("testQueue2"))

    // When
    val queueUrls = client.listQueues().getQueueUrls

    // Then
    queueUrls.size() must be (2)

    val setOfQueueUrls = Set() ++ queueUrls
    setOfQueueUrls.find(_.contains("testQueue1")) must be ('defined)
    setOfQueueUrls.find(_.contains("testQueue2")) must be ('defined)
  }

  test("should list queues with the specified prefix") {
    // Given
    client.createQueue(new CreateQueueRequest("aaaQueue"))
    client.createQueue(new CreateQueueRequest("bbbQueue"))

    // When
    val queueUrls = client.listQueues(new ListQueuesRequest().withQueueNamePrefix("aaa")).getQueueUrls

    // Then
    queueUrls.size() must be (1)
    queueUrls.get(0) must include ("aaaQueue")
  }

  test("should create and delete a queue") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.deleteQueue(new DeleteQueueRequest(queueUrl))

    // Then
    client.listQueues().getQueueUrls.size() must be (0)
  }

  test("should get queue visibility timeout") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueVisibilityTimeout(queueUrl) must be (30)
  }

  test("should set queue visibility timeout") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(visibilityTimeoutAttribute -> "10")))

    // Then
    queueVisibilityTimeout(queueUrl) must be (10)
  }

  test("should send and receive a message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val message = receiveSingleMessage(queueUrl)

    // Then
    message must be (Some("Message 1"))
  }

  test("should receive two messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    // Then
    val bodies = messages.map(_.getBody).toSet
    bodies must be (Set("Message 1", "Message 2"))
  }

  test("should receive no more than the given amount of messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 10) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages

    // Then
    messages must have size (4)
  }

  test("should receive less messages if no messages are available") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 9) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10)).getMessages

    // Then
    messages must have size (9)
  }

  test("should send two messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val result = client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
      new SendMessageBatchRequestEntry("1", "Message 1"),
      new SendMessageBatchRequestEntry("2", "Message 2")
    ))

    // Then
    result.getSuccessful must have size (2)
    result.getSuccessful.map(_.getId).toSet must be (Set("1", "2"))

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.map(_.getBody).toSet
    bodies must be (Set("Message 1", "Message 2"))

    messages.map(_.getMessageId).toSet must be (result.getSuccessful.map(_.getMessageId).toSet)
  }

  test("should block message for the visibility timeout duration") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val m1 = receiveSingleMessage(queueUrl)
    val m2 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    m1 must be (Some("Message 1"))
    m2 must be (None)
    m3 must be (Some("Message 1"))
  }

  test("should block message for the specified non-default visibility timeout duration") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withVisibilityTimeout(2)).getMessages.get(0).getBody
    val m2 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m4 = receiveSingleMessage(queueUrl)

    // Then
    m1 must be ("Message 1")
    m2 must be (None)
    m3 must be (None)
    m4 must be (Some("Message 1"))
  }

  test("should delete a message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0)
    client.deleteMessage(new DeleteMessageRequest(queueUrl, m1.getReceiptHandle))
    Thread.sleep(1100)
    val m2 = receiveSingleMessage(queueUrl)

    // Then
    m1.getBody must be ("Message 1")
    m2 must be (None)
  }

  test("should delete messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
      new SendMessageBatchRequestEntry("1", "Message 1"),
      new SendMessageBatchRequestEntry("2", "Message 2")
    ))

    val msgIds = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.map(_.getReceiptHandle)

    // When
    val result = client.deleteMessageBatch(new DeleteMessageBatchRequest(queueUrl).withEntries(
      new DeleteMessageBatchRequestEntry("1", msgIds(0)),
      new DeleteMessageBatchRequestEntry("2", msgIds(1))
    ))
    Thread.sleep(1100)

    val m = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.map(_.getId).toSet must be (Set("1", "2"))
    m must be (None) // messages deleted
  }

  test("should update message visibility timeout") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    val msgId = client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, msgId, 2))

    val m1 = receiveSingleMessage(queueUrl)

    Thread.sleep(1100) // Queue vis timeout - 1 second. The message shouldn't be received yet
    val m2 = receiveSingleMessage(queueUrl)

    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    m1 must be (None)
    m2 must be (None)
    m3 must be (Some("Message 1"))
  }

  test("should update message visibility timeout in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    val msgId1 = client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    val msgId2 = client.sendMessage(new SendMessageRequest(queueUrl, "Message 2")).getMessageId

    val result = client.changeMessageVisibilityBatch(new ChangeMessageVisibilityBatchRequest().withQueueUrl(queueUrl)
      .withEntries(
      new ChangeMessageVisibilityBatchRequestEntry("1", msgId1).withVisibilityTimeout(2),
      new ChangeMessageVisibilityBatchRequestEntry("2", msgId2).withVisibilityTimeout(2)
    ))

    val m1 = receiveSingleMessage(queueUrl)

    Thread.sleep(1100) // Queue vis timeout - 1 second. Both messages shouldn't be received yet
    val m2 = receiveSingleMessage(queueUrl)

    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    val m4 = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.map(_.getId).toSet must be (Set("1", "2"))

    m1 must be (None)
    m2 must be (None)
    Set(m3, m4) must be (Set(Some("Message 1"), Some("Message 2")))
  }

  test("should read all queue attributes") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 4").withDelaySeconds(2))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 5").withDelaySeconds(2))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 6").withDelaySeconds(2))
    receiveSingleMessage(queueUrl) // two should remain visible, the received one - invisible

    // When
    val attributes = client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes

    // Then
    attributes.get("ApproximateNumberOfMessages") must be ("2")
    attributes.get("ApproximateNumberOfMessagesNotVisible") must be ("1")
    attributes.get("ApproximateNumberOfMessagesDelayed") must be ("3")
    attributes must contain key ("CreatedTimestamp")
    attributes must contain key ("LastModifiedTimestamp")
    attributes must contain key (visibilityTimeoutAttribute)
    attributes must contain key (delaySecondsAttribute)
  }

  test("should read single queue attribute") {
    // Given
    val approximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"

    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    // When
    val approximateNumberOfMessages = client
      .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames(approximateNumberOfMessagesAttribute))
      .getAttributes
      .get(approximateNumberOfMessagesAttribute)
      .toLong

    // Then
    approximateNumberOfMessages must be (1)
  }

  test("should receive message with statistics") {
    // Given
    val start = System.currentTimeMillis()
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val sentTimestampAttribute = "SentTimestamp"
    val approximateReceiveCountAttribute = "ApproximateReceiveCount"
    val approximateFirstReceiveTimestampAttribute = "ApproximateFirstReceiveTimestamp"

    def receiveMessages(): java.util.List[Message] = {
      client.receiveMessage(new ReceiveMessageRequest(queueUrl)
        .withAttributeNames(sentTimestampAttribute, approximateReceiveCountAttribute, approximateFirstReceiveTimestampAttribute))
        .getMessages
    }

    // When
    val messageArray1 = receiveMessages()
    Thread.sleep(1100)
    val messageArray2 = receiveMessages()

    // Then
    messageArray1.size() must be (1)
    val sent1 = messageArray1.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent1 must be >= (start)
    messageArray1.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt must be (1)

    val approxReceive1 = messageArray1.get(0).getAttributes.get(approximateFirstReceiveTimestampAttribute).toLong
    approxReceive1 must be >= (start)

    messageArray2.size() must be (1)
    val sent2 = messageArray2.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent2 must be >= (start)
    messageArray2.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt must be (2)
    messageArray2.get(0).getAttributes.get(approximateFirstReceiveTimestampAttribute).toLong must be >= (approxReceive1)

    sent1 must be (sent2)
  }

  test("should send delayed message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withDelaySeconds(1)).getMessageId

    val m1 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m2 = receiveSingleMessage(queueUrl)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    m1 must be (None)
    m2 must be (Some("Message 1"))
    m3 must be (None)
  }

  test("should create delayed queue") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1").withAttributes(Map(delaySecondsAttribute -> "1")))
      .getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId

    val m1 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m2 = receiveSingleMessage(queueUrl)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    m1 must be (None)
    m2 must be (Some("Message 1"))
    m3 must be (None)
  }

  test("should get queue delay") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueDelay(queueUrl) must be (0)
  }

  test("should set queue delay") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(delaySecondsAttribute -> "10")))

    // Then
    queueDelay(queueUrl) must be (10)
  }

  // Errors

  test("should return an error if strict & trying to receive more than 10 messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    strictOnlyShouldThrowException {
      _.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(11))
    }
  }

  test("should return an error if an id is duplicate in a batch request") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val result = catching(classOf[AmazonServiceException]) either {
      client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "Message 1"),
        new SendMessageBatchRequestEntry("1", "Message 2")
      ))
    }

    // Then
    result.isLeft must be (true)
  }

  test("should return an error if strict & sending too many messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    strictOnlyShouldThrowException {
      _.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
        (for (i <- 1 to 11) yield new SendMessageBatchRequestEntry(i.toString, "Message")): _*
      ))
    }
  }

  test("should return an error if queue name contains invalid characters") {
    // When
    val result = catching(classOf[AmazonServiceException]) either {
      client.createQueue(new CreateQueueRequest("queue with spaces"))
    }

    // Then
    result.isLeft must be (true)
  }

  test("should return an error if strict & sending an invalid character") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    strictOnlyShouldThrowException {
      _.sendMessage(new SendMessageRequest(queueUrl, "\u0000"))
    }
  }

  test("should return a failure for one message, send another if strict & sending two messages in a batch, one with illegal characters") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val result = client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
      new SendMessageBatchRequestEntry("1", "OK"),
      new SendMessageBatchRequestEntry("2", "\u0000")
    ))

    // Then
    result.getSuccessful must have size (1)
    result.getSuccessful.get(0).getId must be ("1")

    result.getFailed must have size (1)
    result.getFailed.get(0).getId must be ("2")

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.map(_.getBody).toSet
    bodies must be (Set("OK"))
  }

  def queueVisibilityTimeout(queueUrl: String) = getQueueLongAttribute(queueUrl, visibilityTimeoutAttribute)

  def queueDelay(queueUrl: String) = getQueueLongAttribute(queueUrl, delaySecondsAttribute)

  def getQueueLongAttribute(queueUrl: String, attributeName: String) = {
    client
      .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames(attributeName))
      .getAttributes.get(attributeName)
      .toLong
  }
  
  def receiveSingleMessage(queueUrl: String) = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages
    if (messages.size() == 0) {
      None
    } else {
      Some(messages.get(0).getBody)
    }
  }

  def strictOnlyShouldThrowException(body: AmazonSQS => Unit) {
    // When
    val resultStrict = catching(classOf[AmazonServiceException]) either body(client)
    val resultRelaxed = catching(classOf[AmazonServiceException]) either body(relaxedClient)

    // Then
    resultStrict.isLeft must be (true)
    resultRelaxed.isRight must be (true)
  }
}