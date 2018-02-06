package org.elasticmq.rest.sqs

import java.nio.ByteBuffer

import org.scalatest.Matchers
import org.scalatest._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import com.amazonaws.services.sqs.model._
import scala.util.control.Exception._
import com.amazonaws.AmazonServiceException
import org.elasticmq.util.Logging
import org.elasticmq._

class AmazonJavaSdkTestSuite extends FunSuite with Matchers with BeforeAndAfter with Logging {
  val visibilityTimeoutAttribute = "VisibilityTimeout"
  val defaultVisibilityTimeoutAttribute = "VisibilityTimeout"
  val redrivePolicyAttribute = "RedrivePolicy"
  val delaySecondsAttribute = "DelaySeconds"
  val receiveMessageWaitTimeSecondsAttribute = "ReceiveMessageWaitTimeSeconds"

  var client: AmazonSQS = _ // strict server
  var relaxedClient: AmazonSQS = _

  var currentTestName: String = _

  var strictServer: SQSRestServer = _
  var relaxedServer: SQSRestServer = _

  before {
    logger.info(s"\n---\nRunning test: $currentTestName\n---\n")

    strictServer = SQSRestServerBuilder
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321))
      .start()

    relaxedServer = SQSRestServerBuilder
      .withPort(9322)
      .withServerAddress(NodeAddress(port = 9322))
      .withSQSLimits(SQSLimits.Relaxed)
      .start()

    strictServer.waitUntilStarted()
    relaxedServer.waitUntilStarted()

    client = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    client.setEndpoint("http://localhost:9321")

    relaxedClient = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    relaxedClient.setEndpoint("http://localhost:9322")
  }

  after {
    client.shutdown()
    relaxedClient.shutdown()

    strictServer.stopAndWait()
    relaxedServer.stopAndWait()

    logger.info(s"\n---\nTest done: $currentTestName\n---\n")
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
    queueUrl should include("testQueue1")
  }

  test("should create a queue with the specified visibility timeout") {
    // When
    client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14")))

    // Then
    val queueUrls = client.listQueues().getQueueUrls

    queueUrls.size() should be(1)

    queueVisibilityTimeout(queueUrls.get(0)) should be(14)
  }

  test("should return an error if strict & queue name is too long") {
    strictOnlyShouldThrowException { cli =>
      cli.createQueue(new CreateQueueRequest("x" * 81))
    }
  }

  test("should list created queues") {
    // Given
    client.createQueue(new CreateQueueRequest("testQueue1"))
    client.createQueue(new CreateQueueRequest("testQueue2"))

    // When
    val queueUrls = client.listQueues().getQueueUrls

    // Then
    queueUrls.size() should be(2)

    val setOfQueueUrls = Set() ++ queueUrls
    setOfQueueUrls.find(_.contains("testQueue1")) should be('defined)
    setOfQueueUrls.find(_.contains("testQueue2")) should be('defined)
  }

  test("should list queues with the specified prefix") {
    // Given
    client.createQueue(new CreateQueueRequest("aaaQueue"))
    client.createQueue(new CreateQueueRequest("bbbQueue"))

    // When
    val queueUrls = client.listQueues(new ListQueuesRequest().withQueueNamePrefix("aaa")).getQueueUrls

    // Then
    queueUrls.size() should be(1)
    queueUrls.get(0) should include("aaaQueue")
  }

  test("should create and delete a queue") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.deleteQueue(new DeleteQueueRequest(queueUrl))

    // Then
    client.listQueues().getQueueUrls.size() should be(0)
  }

  test("should get queue visibility timeout") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueVisibilityTimeout(queueUrl) should be(30)
  }

  test("should set queue visibility timeout") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(visibilityTimeoutAttribute -> "10")))

    // Then
    queueVisibilityTimeout(queueUrl) should be(10)
  }

  test("should send and receive a simple message") {
    doTestSendAndReceiveMessage("Message 1")
  }

  test("should send and receive a simple message with message attributes") {
    doTestSendAndReceiveMessageWithAttributes("Message 1", Map(
      "red" -> StringMessageAttribute("fish"),
      "blue" -> StringMessageAttribute("cat"),
      "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
      "yellow" -> NumberMessageAttribute("1234567890")
    ))
  }

  test("should send a simple message with message attributes and only receive requested attributes") {
    doTestSendAndReceiveMessageWithAttributes("Message 1", Map(
      "red" -> StringMessageAttribute("fish"),
      "blue" -> StringMessageAttribute("cat"),
      "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
      "yellow" -> NumberMessageAttribute("1234567890")
    ), List("red", "green"))
  }

  test("should send a simple message with message attributes and only receive no requested attributes by default") {
    doTestSendAndReceiveMessageWithAttributes("Message 1", Map(
      "red" -> StringMessageAttribute("fish"),
      "blue" -> StringMessageAttribute("cat"),
      "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
      "yellow" -> NumberMessageAttribute("1234567890")
    ), List())
  }

  test("should send and receive a message with caret return and new line characters") {
    doTestSendAndReceiveMessage("a\rb\r\nc\nd")
  }

  test("should send and receive a message with all allowed 1-byte sqs characters") {
    val builder = new StringBuilder
    builder.append(0x9).append(0xA).append(0xD)
    appendRange(builder, 0x20, 0xFF)

    doTestSendAndReceiveMessage(builder.toString())
  }

  test("should send and receive a message with some 2-byte characters") {
    val builder = new StringBuilder
    appendRange(builder, 0x51F9, 0x5210)
    appendRange(builder, 0x30C9, 0x30FF)

    doTestSendAndReceiveMessage(builder.toString())
  }

  def doTestSendAndReceiveMessageWithAttributes(content: String,
                                                messageAttributes: Map[String, MessageAttribute],
                                                requestedAttributes: List[String]) {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val sendMessage = messageAttributes.foldLeft(new SendMessageRequest(queueUrl, content)) { case (message, (k, v)) =>
      val attr = new MessageAttributeValue()
      attr.setDataType(v.getDataType())

      v match {
        case s: StringMessageAttribute => attr.setStringValue(s.stringValue)
        case n: NumberMessageAttribute => attr.setStringValue(n.stringValue)
        case b: BinaryMessageAttribute => attr.setBinaryValue(ByteBuffer.wrap(b.binaryValue))
      }

      message.addMessageAttributesEntry(k, attr)
    }

    client.sendMessage(sendMessage)
    val message = receiveSingleMessageObject(queueUrl, requestedAttributes).orNull

    // Then
    message.getBody should be(content)
    checkMessageAttributesMatchRequestedAttributes(messageAttributes, requestedAttributes, sendMessage, message)
  }

  private def checkMessageAttributesMatchRequestedAttributes(messageAttributes: Map[String, MessageAttribute],
                                                             requestedAttributes: List[String],
                                                             sendMessage: SendMessageRequest,
                                                             message: Message) = {
    val filteredSendMessageAttr = filterBasedOnRequestedAttributes(requestedAttributes, sendMessage.getMessageAttributes.toMap).asJava
    val filteredMessageAttributes = filterBasedOnRequestedAttributes(requestedAttributes, messageAttributes)

    message.getMessageAttributes should be(filteredSendMessageAttr) // Checks they match
    message.getMessageAttributes.map { case (k, attr) =>
      (k, if (attr.getDataType.startsWith("String") && attr.getStringValue != null) {
        StringMessageAttribute(attr.getStringValue).stringValue
      } else if (attr.getDataType.startsWith("Number") && attr.getStringValue != null) {
        NumberMessageAttribute(attr.getStringValue).stringValue
      } else {
        BinaryMessageAttribute.fromByteBuffer(attr.getBinaryValue).asBase64
      })
    } should be(filteredMessageAttributes.map { case (k, attr) =>
      (k, attr match {
        case s: StringMessageAttribute => s.stringValue
        case n: NumberMessageAttribute => n.stringValue
        case b: BinaryMessageAttribute => b.asBase64
      })
    }) // Checks they match map
  }

  private def filterBasedOnRequestedAttributes[T](requestedAttributes: List[String], messageAttributes: Map[String, T]): Map[String, T] = {
    if (requestedAttributes.contains("All")) {
      messageAttributes
    } else {
      messageAttributes.filterKeys(k => requestedAttributes.contains(k))
    }
  }


  // Alias for send and receive with no attributes
  def doTestSendAndReceiveMessage(content: String) {
    doTestSendAndReceiveMessageWithAttributes(content, Map(), List())
  }

  def doTestSendAndReceiveMessageWithAttributes(content: String, messageAttributes: Map[String, MessageAttribute]): Unit = {
    doTestSendAndReceiveMessageWithAttributes(content, messageAttributes, List("All"))
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
    bodies should be(Set("Message 1", "Message 2"))
  }

  test("FIFO queues should return an error if the queue's name does not end in .fifo") {
    val result = catching(classOf[AmazonServiceException]) either {
      val createRequest = new CreateQueueRequest("testQueue1").addAttributesEntry("FifoQueue", "true")
      client.createQueue(createRequest)
    }

    result.isLeft should be(true)
  }

  test("FIFO queues should return an error if an invalid message group id parameter is provided") {
    // Given
    val fifoQueueUrl = client.createQueue(new CreateQueueRequest("testQueue.fifo")
        .addAttributesEntry("FifoQueue", "true")
        .addAttributesEntry("ContentBasedDeduplication", "true")).getQueueUrl
    val regularQueueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // An illegal character
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageGroupId("æ"))
    }

    // More than 128 characters
    val id = (for (_ <- 0 to 300) yield "1").mkString("")
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageGroupId(id))
    }

    // Message group IDs are required for fifo queues
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body"))
    }

    // Regular queues don't allow message groups
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(regularQueueUrl, "A body").withMessageGroupId("group-1"))
    }
  }

  test("FIFO queues do not support delaying individual messages") {
    val queueUrl = createFifoQueue()
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(queueUrl, "body")
          .withMessageDeduplicationId("1")
          .withMessageGroupId("1")
          .withDelaySeconds(10)
      )
    }

    val result = client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
      new SendMessageBatchRequestEntry("1", "Message 1").withMessageGroupId("1"),
      new SendMessageBatchRequestEntry("2", "Message 2").withMessageGroupId("2").withDelaySeconds(10)
    ))
    result.getSuccessful should have size 1
    result.getFailed should have size 1
  }

  test("FIFO provided message group ids should take priority over content based deduplication") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "body").withMessageDeduplicationId("1").withMessageGroupId("1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "body").withMessageDeduplicationId("2").withMessageGroupId("1"))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    // Then
    messages should have size 2
  }

  test("FIFO queues should return an error if an invalid message deduplication id parameter is provided") {
    // Given
    val fifoQueueUrl = client.createQueue(new CreateQueueRequest("testQueue.fifo")
        .addAttributesEntry("FifoQueue", "true")
        .addAttributesEntry("ContentBasedDeduplication", "true")).getQueueUrl
    val regularQueueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // An illegal character
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageDeduplicationId("æ"))
    }

    // More than 128 characters
    val id = (for (_ <- 0 to 300) yield "1").mkString("")
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageDeduplicationId(id))
    }

    // Regular queues don't allow message deduplication
    an[AmazonSQSException] shouldBe thrownBy {
      client.sendMessage(new SendMessageRequest(regularQueueUrl, "A body").withMessageDeduplicationId("dedup-1"))
    }
  }

  test("FIFO queues need a content based deduplication strategy when no message deduplication ids are provided") {
    // Given
    val noStrategyRequest = new CreateQueueRequest("testQueue1.fifo")
        .addAttributesEntry("FifoQueue", "true")
    val noStrategyQueueUrl = client.createQueue(noStrategyRequest).getQueueUrl
    val withStrategyRequest = new CreateQueueRequest("testQueue2.fifo")
        .addAttributesEntry("FifoQueue", "true")
        .addAttributesEntry("ContentBasedDeduplication", "true")
    val withStrategyQueueUrl = client.createQueue(withStrategyRequest).getQueueUrl

    // When
    val result1 = catching(classOf[AmazonSQSException]) either {
      client.sendMessage(new SendMessageRequest(noStrategyQueueUrl, "No strategy").withMessageGroupId("g1"))
    }
    val result2 = catching(classOf[AmazonSQSException]) either {
      client.sendMessage(new SendMessageRequest(withStrategyQueueUrl, "With strategy").withMessageGroupId("g1"))
    }

    // Then
    result1.isLeft should be(true)
    result2.isLeft should be(false)
  }

  test("FIFO queues should not return a second message for the same message group if the first has not been deleted yet") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withMessageGroupId("group-1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2").withMessageGroupId("group-1"))

    val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages
    val messages3 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages
    client.deleteMessage(queueUrl, messages2.head.getReceiptHandle)
    val messages4 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages

    // Then
    messages1 should have size 0
    messages2.map(_.getBody).toSet should be(Set("Message 1"))
    messages3 should have size 0
    messages4.map(_.getBody).toSet should be(Set("Message 2"))
  }

  test("FIFO queues should not return a second message for messages without a message group id if the first has not been deleted yet") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withMessageGroupId("group1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2").withMessageGroupId("group1"))

    val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages
    val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages

    // Then
    messages1 should have size 1
    messages2 should have size 0
  }


  test("FIFO queues should block messages for the visibility timeout period within a message group") {
    // Given
    val queueUrl = createFifoQueue(attributes = Map(defaultVisibilityTimeoutAttribute -> "1"))

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withMessageGroupId("group"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2").withMessageGroupId("group"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 3").withMessageGroupId("group"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 4").withMessageGroupId("group"))

    val m1 = receiveSingleMessage(queueUrl)
    val m2 = receiveSingleMessage(queueUrl)
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    // The message that got delivered in the first request should not reappear in the second, it should become available
    // after the default visibility timeout has passed however
    m1 should be(Some("Message 1"))
    m2 should be(empty)
    m3 should be(Some("Message 1"))
  }

  test("FIFO queues should deliver batches of messages from the same message group") {
    // Given
    val queueUrl = createFifoQueue()

    // When sending 4 distinct messages (based on dedup id), two for each message group
    val group1 = "group1"
    val group2 = "group2"
    client.sendMessage(new SendMessageRequest(queueUrl, group1).withMessageGroupId(group1).withMessageDeduplicationId("1"))
    client.sendMessage(new SendMessageRequest(queueUrl, group1).withMessageGroupId(group1).withMessageDeduplicationId("2"))
    client.sendMessage(new SendMessageRequest(queueUrl, group2).withMessageGroupId(group2).withMessageDeduplicationId("3"))
    client.sendMessage(new SendMessageRequest(queueUrl, group2).withMessageGroupId(group2).withMessageDeduplicationId("4"))

    val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages
    val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    // Then
    // When requesting 2 messages at a time, the first request should return 2 messages from group 1 (resp group 2) and
    // the second request should return 2 messages from group 2 (resp. group 1).
    messages1 should have size 2
    messages1.map(_.getBody).toSet should have size 1
    messages2 should have size 2
    messages2.map(_.getBody).toSet should have size 1
  }

  test("FIFO queues should deliver messages in the same order as they are sent") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val messageBodies = for (i <- 1 to 20) yield s"Message $i"
    messageBodies.map(body => client.sendMessage(new SendMessageRequest(queueUrl, body).withMessageGroupId("group1")))

    // Then
    val deliveredSingleReceives = messageBodies.take(10).map { _ =>
      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages
      client.deleteMessage(queueUrl, messages.head.getReceiptHandle)
      messages.head
    }
    val batchReceive = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10)).getMessages

    val allMessages = deliveredSingleReceives ++ batchReceive
    allMessages.map(_.getBody) should be(messageBodies)
  }

  test("FIFO queues should deduplicate messages based on the message body") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val sentMessages = for (_ <- 1 to 10) yield {
      client.sendMessage(new SendMessageRequest(queueUrl, "Message").withMessageGroupId("1"))
    }
    val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages
    client.deleteMessage(queueUrl, messages1.head.getReceiptHandle)
    val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages


    // Then
    sentMessages.map(_.getMessageId).toSet should have size 1
    messages1.map(_.getBody) should have size 1
    messages1.map(_.getBody).toSet should be(Set("Message"))
    messages2 should have size 0
  }

  test("FIFO queues should deduplicate messages based on the message deduplication attribute") {
    val queueUrl = createFifoQueue()

    // When
    for (i <- 1 to 10) {
      client.sendMessage(new SendMessageRequest(queueUrl, s"Message $i")
          .withMessageDeduplicationId("DedupId")
          .withMessageGroupId("1"))
    }

    val m1 = receiveSingleMessageObject(queueUrl)
    client.deleteMessage(queueUrl, m1.get.getReceiptHandle)
    val m2 = receiveSingleMessage(queueUrl)


    // Then
    m1.map(_.getBody) should be(Some("Message 1"))
    m2 should be(empty)
  }

  private def createFifoQueue(suffix: Int = 1, attributes: Map[String, String] = Map.empty): String = {
    val createRequest1 = new CreateQueueRequest(s"testFifoQueue$suffix.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "true")
    val createRequest2 = attributes.foldLeft(createRequest1) { case (acc, (k, v)) => acc.addAttributesEntry(k, v) }
    client.createQueue(createRequest2).getQueueUrl
  }

  test("should receive no more than the given amount of messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 10) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages

    // Then
    messages should have size (4)
  }

  test("should receive less messages if no messages are available") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 9) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10)).getMessages

    // Then
    messages should have size (9)
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
    result.getSuccessful should have size (2)
    result.getSuccessful.map(_.getId).toSet should be(Set("1", "2"))

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.map(_.getBody).toSet
    bodies should be(Set("Message 1", "Message 2"))

    messages.map(_.getMessageId).toSet should be(result.getSuccessful.map(_.getMessageId).toSet)
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
    m1 should be(Some("Message 1"))
    m2 should be(None)
    m3 should be(Some("Message 1"))
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
    m1 should be("Message 1")
    m2 should be(None)
    m3 should be(None)
    m4 should be(Some("Message 1"))
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
    m1.getBody should be("Message 1")
    m2 should be(None)
  }

  test("should delete messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    client.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
      new SendMessageBatchRequestEntry("1", "Message 1"),
      new SendMessageBatchRequestEntry("2", "Message 2")
    ))

    val msgReceipts = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.map(_.getReceiptHandle)

    // When
    val result = client.deleteMessageBatch(new DeleteMessageBatchRequest(queueUrl).withEntries(
      new DeleteMessageBatchRequestEntry("1", msgReceipts(0)),
      new DeleteMessageBatchRequestEntry("2", msgReceipts(1))
    ))
    Thread.sleep(1100)

    val m = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.map(_.getId).toSet should be(Set("1", "2"))
    m should be(None) // messages deleted
  }

  test("should update message visibility timeout") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "5"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.apply(0)
    client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, m1.getReceiptHandle, 1))

    val m2 = receiveSingleMessage(queueUrl)

    // Message should be already visible
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)

    // Then
    m1.getBody should be("Message 1")
    m2 should be(None)
    m3 should be(Some("Message 1"))
  }

  test("should return an error when trying to change the visibility timeout of an unknown message") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "5"))).getQueueUrl

    an[AmazonSQSException] shouldBe thrownBy {
      client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, "Unknown receipt handle", 1))
    }
  }

  test("should update message visibility timeout in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "5"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2")).getMessageId

    val msg1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.apply(0)
    val msg2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.apply(0)

    val result = client.changeMessageVisibilityBatch(new ChangeMessageVisibilityBatchRequest().withQueueUrl(queueUrl)
      .withEntries(
        new ChangeMessageVisibilityBatchRequestEntry("1", msg1.getReceiptHandle).withVisibilityTimeout(1),
        new ChangeMessageVisibilityBatchRequestEntry("2", msg2.getReceiptHandle).withVisibilityTimeout(1)
      ))

    // Messages should be already visible
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    val m4 = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.map(_.getId).toSet should be(Set("1", "2"))

    Set(m3, m4) should be(Set(Some("Message 1"), Some("Message 2")))
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
    attributes.get("ApproximateNumberOfMessages") should be("2")
    attributes.get("ApproximateNumberOfMessagesNotVisible") should be("1")
    attributes.get("ApproximateNumberOfMessagesDelayed") should be("3")
    attributes should contain key ("CreatedTimestamp")
    attributes should contain key ("LastModifiedTimestamp")
    attributes should contain key (visibilityTimeoutAttribute)
    attributes should contain key (delaySecondsAttribute)
    attributes should contain key (receiveMessageWaitTimeSecondsAttribute)
    attributes should contain key ("QueueArn")
  }

  test("should return proper queue statistics after receiving, deleting a message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    def verifyQueueAttributes(expectedMsgs: Int, expectedNotVisible: Int, expectedDelayed: Int) {
      val attributes = client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes

      attributes.get("ApproximateNumberOfMessages") should be(expectedMsgs.toString)
      attributes.get("ApproximateNumberOfMessagesNotVisible") should be(expectedNotVisible.toString)
      attributes.get("ApproximateNumberOfMessagesDelayed") should be(expectedDelayed.toString)
    }

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))

    // Then
    verifyQueueAttributes(2, 0, 0)

    // When
    val receiptHandle = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0).getReceiptHandle

    // Then
    verifyQueueAttributes(1, 1, 0)

    // When
    client.deleteMessage(new DeleteMessageRequest(queueUrl, receiptHandle))

    // Then
    verifyQueueAttributes(1, 0, 0)
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
    approximateNumberOfMessages should be(1)
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
    messageArray1.size() should be(1)
    val sent1 = messageArray1.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent1 should be >= (start)
    messageArray1.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt should be(1)

    val approxReceive1 = messageArray1.get(0).getAttributes.get(approximateFirstReceiveTimestampAttribute).toLong
    approxReceive1 should be >= (start)

    messageArray2.size() should be(1)
    val sent2 = messageArray2.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent2 should be >= (start)
    messageArray2.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt should be(2)
    messageArray2.get(0).getAttributes.get(approximateFirstReceiveTimestampAttribute).toLong should be >= (approxReceive1)

    sent1 should be(sent2)
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
    m1 should be(None)
    m2 should be(Some("Message 1"))
    m3 should be(None)
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
    m1 should be(None)
    m2 should be(Some("Message 1"))
    m3 should be(None)
  }

  test("should get queue delay") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueDelay(queueUrl) should be(0)
  }

  test("should set queue delay") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(delaySecondsAttribute -> "10")))

    // Then
    queueDelay(queueUrl) should be(10)
  }

  test("should create queue with message wait") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1").withAttributes(Map(receiveMessageWaitTimeSecondsAttribute -> "1")))
      .getQueueUrl

    // When
    val start = System.currentTimeMillis()
    val m1 = receiveSingleMessage(queueUrl)
    val end = System.currentTimeMillis()

    // Then
    m1 should be(None)
    (end - start) should be >= (1000L)
  }

  test("should get queue receive message wait") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueReceiveMessageWaitTimeSeconds(queueUrl) should be(0)
  }

  test("should set queue receive message wait") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(receiveMessageWaitTimeSecondsAttribute -> "13")))

    // Then
    queueReceiveMessageWaitTimeSeconds(queueUrl) should be(13)
  }

  test("should receive message sent later when waiting for messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val t = new Thread() {
      override def run() {
        Thread.sleep(500L)
        client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      }
    }
    t.start()

    // When
    val start = System.currentTimeMillis()
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(1)).getMessages
    val end = System.currentTimeMillis()

    // Then
    (end - start) should be >= (500L)
    messages.size should be(1)
  }

  // Errors

  test("should return an error if strict & trying to receive more than 10 messages") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(11))
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
    result.isLeft should be(true)
  }

  test("should return an error if strict & sending too many messages in a batch") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
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
    result.isLeft should be(true)
  }

  test("should return an error if strict & sending an invalid character") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      cli.sendMessage(new SendMessageRequest(queueUrl, "\u0000"))
    }
  }

  test("should return an error if strict & message body is too long") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.sendMessage(new SendMessageRequest(queueUrl, "x" * 262145))
    }
  }

  test("should return an error if strict & total message bodies length is too long in a batch send") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "x" * 140000),
        new SendMessageBatchRequestEntry("2", "x" * 140000)
      ))
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
    result.getSuccessful should have size (1)
    result.getSuccessful.get(0).getId should be("1")

    result.getFailed should have size (1)
    result.getFailed.get(0).getId should be("2")

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.map(_.getBody).toSet
    bodies should be(Set("OK"))
  }

  test("should delete a message only if the most recent receipt handle is provided") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0) // 1st receive

    Thread.sleep(1100)
    client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0) // 2nd receive

    client.deleteMessage(new DeleteMessageRequest(queueUrl, m1.getReceiptHandle)) // Shouldn't delete - old receipt

    // Then
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    m3 should be(Some("Message 1"))
  }

  test("should return an error if creating an existing queue with a different visibility timeout") {
    val result = catching(classOf[AmazonServiceException]) either {
      // Given
      client.createQueue(new CreateQueueRequest("testQueue1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "10"))).getQueueUrl

      // When
      client.createQueue(new CreateQueueRequest("testQueue1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14"))).getQueueUrl
    }

    result.isLeft should be(true)
  }

  test("should not return an error if creating an existing queue with a non default visibility timeout") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "42"))).getQueueUrl

    // When
    val queueUrl2 = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl


    queueUrl should equal(queueUrl2)
  }

  test("should purge the queue") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
    client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages.get(0)

    // When
    client.purgeQueue(new PurgeQueueRequest().withQueueUrl(queueUrl))

    // Then
    val attributes = client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes
    attributes.get("ApproximateNumberOfMessages") should be("0")
    attributes.get("ApproximateNumberOfMessagesNotVisible") should be("0")
    attributes.get("ApproximateNumberOfMessagesDelayed") should be("0")
  }

  test("should receive delayed messages when waiting for messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val start = System.currentTimeMillis()
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withDelaySeconds(2))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(4))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < (2500L)
  }

  test("if message visibility changes during a long pool, should receive messages as soon as they become available") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val handle = client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withDelaySeconds(4)).getMessageId
    new Thread {
      override def run() {
        Thread.sleep(1000L)
        client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, handle, 1))
      }
    }.start()

    val start = System.currentTimeMillis()
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(5))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < (2500L) // 1 second waiting with the old visibility, then change to 1 second
  }

  test("should allow 0 as a value for message wait time seconds") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    // When
    val start = System.currentTimeMillis()
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(0))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < (1000L)
  }

  test("should send & receive the same message id") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.purgeQueue(new PurgeQueueRequest(queueUrl))
    val msgIdSent = client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    val msgIdReceived = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(0))
      .getMessages.get(0).getMessageId

    // Then
    msgIdSent should be(msgIdReceived)
  }

  test("should create queue with redrive policy.") {
    // Given
    client.createQueue(new CreateQueueRequest("dlq1"))

    // Then
    client.createQueue(new CreateQueueRequest("q1")
      .withAttributes(Map(
        defaultVisibilityTimeoutAttribute -> "1",
        redrivePolicyAttribute ->
          """
            |{
            |  "deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1",
            |  "maxReceiveCount":"1"
            |}
          """.stripMargin
      )))
  }

  def queueVisibilityTimeout(queueUrl: String) = getQueueLongAttribute(queueUrl, visibilityTimeoutAttribute)

  def queueDelay(queueUrl: String) = getQueueLongAttribute(queueUrl, delaySecondsAttribute)

  def queueReceiveMessageWaitTimeSeconds(queueUrl: String) = getQueueLongAttribute(queueUrl, receiveMessageWaitTimeSecondsAttribute)

  def getQueueLongAttribute(queueUrl: String, attributeName: String) = {
    client
      .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames(attributeName))
      .getAttributes.get(attributeName)
      .toLong
  }

  def receiveSingleMessage(queueUrl: String): Option[String] = {
    receiveSingleMessage(queueUrl, List("All"))
  }

  def receiveSingleMessage(queueUrl: String, requestedAttributes: List[String]): Option[String] = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages
    messages.headOption.map(_.getBody)
  }

  def receiveSingleMessageObject(queueUrl: String): Option[Message] = {
    receiveSingleMessageObject(queueUrl, List("All"))
  }

  def receiveSingleMessageObject(queueUrl: String, requestedAttributes: List[String]): Option[Message] = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMessageAttributeNames(requestedAttributes)).getMessages
    messages.headOption
  }

  def strictOnlyShouldThrowException(body: AmazonSQS => Unit) {
    // When
    val resultStrict = catching(classOf[AmazonServiceException]) either body(client)
    val resultRelaxed = catching(classOf[AmazonServiceException]) either body(relaxedClient)

    // Then
    resultStrict.isLeft should be(true)
    resultRelaxed.isRight should be(true)
  }

  def appendRange(builder: StringBuilder, start: Int, end: Int) {
    var current = start
    while (current <= end) {
      builder.appendAll(Character.toChars(current))
      current += 1
    }
  }

  override protected def runTest(testName: String, args: Args) = {
    currentTestName = testName
    val result = super.runTest(testName, args)
    currentTestName = null
    result
  }
}
