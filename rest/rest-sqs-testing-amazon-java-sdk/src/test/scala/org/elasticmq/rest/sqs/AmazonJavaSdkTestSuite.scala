package org.elasticmq.rest.sqs

import java.nio.ByteBuffer

import org.scalatest.matchers.MustMatchers
import org.scalatest._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClient}

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model._
import scala.util.control.Exception._
import com.amazonaws.AmazonServiceException
import org.elasticmq.util.Logging
import org.elasticmq._

class AmazonJavaSdkTestSuite extends FunSuite with MustMatchers with BeforeAndAfter with Logging {
  val visibilityTimeoutAttribute = "VisibilityTimeout"
  val defaultVisibilityTimeoutAttribute = "VisibilityTimeout"
  val delaySecondsAttribute = "DelaySeconds"
  val receiveMessageWaitTimeSecondsAttribute = "ReceiveMessageWaitTimeSeconds"

  var client: AmazonSQS = _ // strict server
  var relaxedClient: AmazonSQS = _

  var currentTestName: String = _

  var strictServer: SQSRestServer = _
  var relaxedServer: SQSRestServer = _

  before {
    logger.info(s"\n---\nRunning test: $currentTestName\n---\n")

    strictServer  = SQSRestServerBuilder
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
    queueUrl must include ("testQueue1")
  }

  test("should create a queue with the specified visibility timeout") {
    // When
    client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14")))

    // Then
    val queueUrls = client.listQueues().getQueueUrls

    queueUrls.size() must be (1)

    queueVisibilityTimeout(queueUrls.get(0)) must be (14)
  }

  test("should return an error if strict & queue name is too long") {
    strictOnlyShouldThrowException { cli =>
      cli.createQueue(new CreateQueueRequest("x"*81))
    }
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

  def doTestSendAndReceiveMessageWithAttributes(content: String, messageAttributes: Map[String,MessageAttribute]) {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val sendMessage = messageAttributes.foldLeft(new SendMessageRequest(queueUrl, content)){ case (message, (k,v)) =>
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
    val message = receiveSingleMessageObject(queueUrl).orNull

    // Then
    message.getBody must be (content)
    message.getMessageAttributes must be (sendMessage.getMessageAttributes) // Checks they match
    message.getMessageAttributes.map { case (k,attr) =>
      (k,if (attr.getDataType.startsWith("String") && attr.getStringValue != null) {
        StringMessageAttribute(attr.getStringValue).stringValue
      }else if (attr.getDataType.startsWith("Number") && attr.getStringValue != null) {
        NumberMessageAttribute(attr.getStringValue).stringValue
      }else {
        BinaryMessageAttribute.fromByteBuffer(attr.getBinaryValue).asBase64
      })
    } must be (messageAttributes.map { case (k,attr) =>
      (k,attr match {
        case s: StringMessageAttribute => s.stringValue
        case n: NumberMessageAttribute => n.stringValue
        case b: BinaryMessageAttribute => b.asBase64
      })
    }) // Checks they match map
  }

  // Alias for send and receive with no attributes
  def doTestSendAndReceiveMessage(content: String) {
    doTestSendAndReceiveMessageWithAttributes(content, Map())
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

    val msgReceipts = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.map(_.getReceiptHandle)

    // When
    val result = client.deleteMessageBatch(new DeleteMessageBatchRequest(queueUrl).withEntries(
      new DeleteMessageBatchRequestEntry("1", msgReceipts(0)),
      new DeleteMessageBatchRequestEntry("2", msgReceipts(1))
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
    m1.getBody must be ("Message 1")
    m2 must be (None)
    m3 must be (Some("Message 1"))
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
    result.getSuccessful.map(_.getId).toSet must be (Set("1", "2"))

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
    attributes must contain key (receiveMessageWaitTimeSecondsAttribute)
    attributes must contain key ("QueueArn")
  }

  test("should return proper queue statistics after receiving, deleting a message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    def verifyQueueAttributes(expectedMsgs: Int, expectedNotVisible: Int, expectedDelayed: Int) {
      val attributes = client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes

      attributes.get("ApproximateNumberOfMessages") must be (expectedMsgs.toString)
      attributes.get("ApproximateNumberOfMessagesNotVisible") must be (expectedNotVisible.toString)
      attributes.get("ApproximateNumberOfMessagesDelayed") must be (expectedDelayed.toString)
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

  test("should create queue with message wait") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1").withAttributes(Map(receiveMessageWaitTimeSecondsAttribute -> "1")))
      .getQueueUrl

    // When
    val start = System.currentTimeMillis()
    val m1 = receiveSingleMessage(queueUrl)
    val end = System.currentTimeMillis()

    // Then
    m1 must be (None)
    (end - start) must be >= (1000L)
  }

  test("should get queue receive message wait") {
    // When
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // Then
    queueReceiveMessageWaitTimeSeconds(queueUrl) must be (0)
  }

  test("should set queue receive message wait") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(receiveMessageWaitTimeSecondsAttribute -> "13")))

    // Then
    queueReceiveMessageWaitTimeSeconds(queueUrl) must be (13)
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
    (end - start) must be >= (500L)
    messages.size must be (1)
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
    result.isLeft must be (true)
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
    result.isLeft must be (true)
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
      cli.sendMessage(new SendMessageRequest(queueUrl, "x"* 262145))
    }
  }

  test("should return an error if strict & total message bodies length is too long in a batch send") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.sendMessageBatch(new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "x"*140000),
        new SendMessageBatchRequestEntry("2", "x"*140000)
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
    result.getSuccessful must have size (1)
    result.getSuccessful.get(0).getId must be ("1")

    result.getFailed must have size (1)
    result.getFailed.get(0).getId must be ("2")

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.map(_.getBody).toSet
    bodies must be (Set("OK"))
  }

  test("should delete a message only if the most recent receipt handle is provided") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")
      .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1"))).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0)  // 1st receive

    Thread.sleep(1100)
    client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0)           // 2nd receive

    client.deleteMessage(new DeleteMessageRequest(queueUrl, m1.getReceiptHandle))           // Shouldn't delete - old receipt

    // Then
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    m3 must be (Some("Message 1"))
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

    result.isLeft must be (true)
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
    attributes.get("ApproximateNumberOfMessages") must be ("0")
    attributes.get("ApproximateNumberOfMessagesNotVisible") must be ("0")
    attributes.get("ApproximateNumberOfMessagesDelayed") must be ("0")
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
    messages.size must be (1)
    (end - start) must be < (2500L)
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
    messages.size must be (1)
    (end - start) must be < (2500L) // 1 second waiting with the old visibility, then change to 1 second
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
    messages.size must be (1)
    (end - start) must be < (1000L)
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
    msgIdSent must be(msgIdReceived)
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
  
  def receiveSingleMessage(queueUrl: String) = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages
    if (messages.size() == 0) {
      None
    } else {
      Some(messages.get(0).getBody)
    }
  }

  def receiveSingleMessageObject(queueUrl: String) = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages
    if (messages.size() == 0) {
      None
    } else {
      Some(messages.get(0))
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
