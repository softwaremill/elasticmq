package org.elasticmq.rest.sqs

import org.apache.pekko.http.scaladsl.model.StatusCodes
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model._
import org.apache.http.HttpHost
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.message.BasicNameValuePair
import org.elasticmq._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.control.Exception._

class AmazonJavaSdkTestSuite extends SqsClientServerCommunication with Matchers {
  val visibilityTimeoutAttribute = "VisibilityTimeout"
  val defaultVisibilityTimeoutAttribute = "VisibilityTimeout"
  val redrivePolicyAttribute = "RedrivePolicy"
  val delaySecondsAttribute = "DelaySeconds"
  val receiveMessageWaitTimeSecondsAttribute = "ReceiveMessageWaitTimeSeconds"

  test("should create a queue") {
    client.createQueue(new CreateQueueRequest("testQueue1"))
  }

  test("should get queue url") {
    // Given
    client.createQueue(new CreateQueueRequest("testQueue1"))

    // When
    val queueUrl = client.getQueueUrl(new GetQueueUrlRequest("testQueue1")).getQueueUrl

    // Then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should fail to get queue url if queue doesn't exist") {
    // When
    val thrown = intercept[QueueDoesNotExistException] {
      client.getQueueUrl(new GetQueueUrlRequest("testQueue1")).getQueueUrl
    }

    // Then
    thrown.getStatusCode shouldBe 400
  }

  test("should create a queue with the specified visibility timeout") {
    // When
    client.createQueue(
      new CreateQueueRequest("testQueue1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14").asJava)
    )

    // Then
    val queueUrls = client.listQueues().getQueueUrls

    queueUrls.size() should be(1)

    queueVisibilityTimeout(queueUrls.get(0)) should be(14)
  }

  test("should return an error if strict & queue name is too long") {
    strictOnlyShouldThrowException { cli => cli.createQueue(new CreateQueueRequest("x" * 81)) }
  }

  test("should list created queues") {
    // Given
    client.createQueue(new CreateQueueRequest("testQueue1"))
    client.createQueue(new CreateQueueRequest("testQueue2"))

    // When
    val queueUrls = client.listQueues().getQueueUrls

    // Then
    queueUrls.size() should be(2)

    val setOfQueueUrls = Set() ++ queueUrls.asScala
    setOfQueueUrls.find(_.contains("testQueue1")) should be(Symbol("defined"))
    setOfQueueUrls.find(_.contains("testQueue2")) should be(Symbol("defined"))
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

  test("should refer to queue via three URL formats") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("lol")).getQueueUrl

    // When
    val attributes1 = client
      .getQueueAttributes(new GetQueueAttributesRequest("http://localhost:9321/queue/lol").withAttributeNames("All"))
      .getAttributes
    val attributes2 = client
      .getQueueAttributes(
        new GetQueueAttributesRequest("http://localhost:9321/012345678900/lol").withAttributeNames("All")
      )
      .getAttributes
    val attributes3 = client
      .getQueueAttributes(
        new GetQueueAttributesRequest("http://localhost:9321/lol").withAttributeNames("All")
      )
      .getAttributes

    // Then
    attributes1 should be(attributes2)
    attributes1 should be(attributes3)
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
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(visibilityTimeoutAttribute -> "10").asJava))

    // Then
    queueVisibilityTimeout(queueUrl) should be(10)
  }

  test("should send and receive a simple message") {
    doTestSendAndReceiveMessage("Message 1")
  }

  test("should send and receive a simple message with message attributes") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      )
    )
  }

  test("should send a simple message with message attributes and only receive requested attributes") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      List("red", "green", "orange")
    )
  }

  test("should send a simple message with message attributes and only receive no requested attributes by default") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      List()
    )
  }

  test("should send and receive a message with caret return and new line characters") {
    doTestSendAndReceiveMessage("a\rb\r\nc\nd")
  }

  test("should send and receive a message with all allowed 1-byte sqs characters") {
    val builder = new StringBuilder
    builder.append(0x9).append(0xa).append(0xd)
    appendRange(builder, 0x20, 0xff)

    doTestSendAndReceiveMessage(builder.toString())
  }

  test("should send and receive a message with some 2-byte characters") {
    val builder = new StringBuilder
    appendRange(builder, 0x51f9, 0x5210)
    appendRange(builder, 0x30c9, 0x30ff)

    doTestSendAndReceiveMessage(builder.toString())
  }

  test("should send a simple message with message attributes with multi-byte characters") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "innocent" -> StringMessageAttribute("😇")
      )
    )
  }

  test("should respond with text/xml") {
    // given
    client.createQueue(new CreateQueueRequest("testQueue1"))

    val httpHost = new HttpHost("localhost", 9321)
    val req = new HttpGet("/queue/testQueue1?Action=ReceiveMessage")

    // when
    val res = httpClient.execute(httpHost, req)

    // then
    res.getStatusLine.getStatusCode shouldBe StatusCodes.OK.intValue
    res.getEntity.getContentType.getValue should be("text/xml; charset=UTF-8")
  }

  test("should reply with a MissingAction error when no action sent") {
    // given
    val httpHost = new HttpHost("localhost", 9321)
    val req = new HttpPost()
    req.setURI(new URI("/queue/lol"))

    // when
    val res = httpClient.execute(httpHost, req)

    // then
    res.getStatusLine.getStatusCode shouldBe StatusCodes.BadRequest.intValue
    Source.fromInputStream(res.getEntity.getContent).mkString should include("<Code>MissingAction</Code>")
  }

  test("should reply with a InvalidAction error when no action attribute not set") {
    // given
    val httpHost = new HttpHost("localhost", 9321)
    val req = new HttpPost()
    req.setURI(new URI("/queue/lol"))
    val action = new BasicNameValuePair("Action", "")
    req.setEntity(new UrlEncodedFormEntity(List(action).asJava))

    // when
    val res = httpClient.execute(httpHost, req)

    // then
    res.getStatusLine.getStatusCode shouldBe StatusCodes.BadRequest.intValue
    Source.fromInputStream(res.getEntity.getContent).mkString should include("<Code>InvalidAction</Code>")
  }

  test("should reply with 400 and NonExistentQueue if path starts with invalid name") {
    // given
    val httpHost = new HttpHost("localhost", 9321)
    val req = new HttpPost()
    req.setURI(new URI("/wrong-path"))
    val action = new BasicNameValuePair("Action", "ReceiveMessage")
    req.setEntity(new UrlEncodedFormEntity(List(action).asJava))

    // when
    val res = httpClient.execute(httpHost, req)

    // then
    res.getStatusLine.getStatusCode shouldBe StatusCodes.BadRequest.intValue
    Source.fromInputStream(res.getEntity.getContent).mkString should include(
      "<Code>AWS.SimpleQueueService.NonExistentQueue"
    )
  }

  test("should reply with a InvalidAction error when unknown action set") {
    // given
    val httpHost = new HttpHost("localhost", 9321)
    val req = new HttpPost()
    req.setURI(new URI("/queue/lol"))
    val action = new BasicNameValuePair("Action", "Whatever")
    req.setEntity(new UrlEncodedFormEntity(List(action).asJava))

    // when
    val res = httpClient.execute(httpHost, req)

    // then
    res.getStatusLine.getStatusCode shouldBe StatusCodes.BadRequest.intValue
    Source.fromInputStream(res.getEntity.getContent).mkString should include("<Code>InvalidAction</Code>")
  }

  // Alias for send and receive with no attributes
  def doTestSendAndReceiveMessage(content: String): Unit = {
    doTestSendAndReceiveMessageWithAttributes(content, Map(), List())
  }

  def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute]
  ): Unit = {
    doTestSendAndReceiveMessageWithAttributes(content, messageAttributes, List("All"))
  }

  def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute],
      requestedAttributes: List[String]
  ): Unit = {
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

  private def checkMessageAttributesMatchRequestedAttributes(
      messageAttributes: Map[String, MessageAttribute],
      requestedAttributes: List[String],
      sendMessage: SendMessageRequest,
      message: Message
  ) = {
    val filteredSendMessageAttr =
      filterBasedOnRequestedAttributes(requestedAttributes, sendMessage.getMessageAttributes.asScala.toMap).asJava
    val filteredMessageAttributes = filterBasedOnRequestedAttributes(requestedAttributes, messageAttributes)

    message.getMessageAttributes should be(filteredSendMessageAttr) // Checks they match
    message.getMessageAttributes.asScala.map { case (k, attr) =>
      (
        k,
        if (attr.getDataType.startsWith("String") && attr.getStringValue != null) {
          StringMessageAttribute(attr.getStringValue).stringValue
        } else if (attr.getDataType.startsWith("Number") && attr.getStringValue != null) {
          NumberMessageAttribute(attr.getStringValue).stringValue
        } else {
          BinaryMessageAttribute.fromByteBuffer(attr.getBinaryValue).asBase64
        }
      )
    } should be(filteredMessageAttributes.map { case (k, attr) =>
      (
        k,
        attr match {
          case s: StringMessageAttribute => s.stringValue
          case n: NumberMessageAttribute => n.stringValue
          case b: BinaryMessageAttribute => b.asBase64
        }
      )
    }) // Checks they match map
  }

  private def filterBasedOnRequestedAttributes[T](
      requestedAttributes: List[String],
      messageAttributes: Map[String, T]
  ): Map[String, T] = {
    if (requestedAttributes.contains("All")) {
      messageAttributes
    } else {
      messageAttributes.filterKeys(k => requestedAttributes.contains(k)).toMap
    }
  }

  test("should receive two messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    // Then
    val bodies = messages.asScala.map(_.getBody).toSet
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
    val fifoQueueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue.fifo")
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true")
      )
      .getQueueUrl
    val regularQueueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // An illegal character
    assertInvalidParameterException("MessageGroupId")(
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageGroupId("æ"))
    )

    // More than 128 characters
    val id = (for (_ <- 0 to 300) yield "1").mkString("")
    assertInvalidParameterException("MessageGroupId")(
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body").withMessageGroupId(id))
    )

    // Message group IDs are required for fifo queues
    assertMissingParameterException("MessageGroupId")(
      client.sendMessage(new SendMessageRequest(fifoQueueUrl, "A body"))
    )

    // Regular queues don't allow message groups
    assertInvalidParameterQueueTypeException("MessageGroupId")(
      client.sendMessage(new SendMessageRequest(regularQueueUrl, "A body").withMessageGroupId("group-1"))
    )
  }

  test("FIFO queues do not support delaying individual messages") {
    val queueUrl = createFifoQueue()
    assertInvalidParameterException("DelaySeconds")(
      client.sendMessage(
        new SendMessageRequest(queueUrl, "body")
          .withMessageDeduplicationId("1")
          .withMessageGroupId("1")
          .withDelaySeconds(10)
      )
    )

    val result = client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "Message 1").withMessageGroupId("1"),
        new SendMessageBatchRequestEntry("2", "Message 2").withMessageGroupId("2").withDelaySeconds(10)
      )
    )
    result.getSuccessful should have size 1
    result.getFailed should have size 1

    // Sanity check that a 0 delay seconds value is accepted
    client.sendMessage(
      new SendMessageRequest(queueUrl, "body")
        .withMessageDeduplicationId("1")
        .withMessageGroupId("1")
        .withDelaySeconds(0)
    )
  }

  test("FIFO queues should return an error if an invalid message deduplication id parameter is provided") {
    // Given
    val fifoQueueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue.fifo")
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true")
      )
      .getQueueUrl
    val regularQueueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // An illegal character
    assertInvalidParameterException("MessageDeduplicationId")(
      client.sendMessage(
        new SendMessageRequest(fifoQueueUrl, "A body")
          .withMessageGroupId("groupId1")
          .withMessageDeduplicationId("æ")
      )
    )

    // More than 128 characters
    val id = (for (_ <- 0 to 300) yield "1").mkString("")
    assertInvalidParameterException("MessageDeduplicationId")(
      client.sendMessage(
        new SendMessageRequest(fifoQueueUrl, "A body")
          .withMessageGroupId("groupId1")
          .withMessageDeduplicationId(id)
      )
    )

    // Regular queues don't allow message deduplication
    assertInvalidParameterQueueTypeException("MessageDeduplicationId")(
      client.sendMessage(new SendMessageRequest(regularQueueUrl, "A body").withMessageDeduplicationId("dedup-1"))
    )
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

  test("FIFO queues should respond quickly during long polling") {
    // Given
    val deadLetterQueue = new CreateQueueRequest(s"dead-testFifoQueue-long-poll.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "false")

    val deadLetterUrl = client.createQueue(deadLetterQueue).getQueueUrl

    val redrivePolicy =
      RedrivePolicy("dead-testFifoQueue-long-poll.fifo", awsRegion, awsAccountId, 1).toJson.toString

    val fifoQueue = new CreateQueueRequest(s"testFifoQueue-long-poll.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "false")
      .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
      .addAttributesEntry(defaultVisibilityTimeoutAttribute, "20")
      .addAttributesEntry(receiveMessageWaitTimeSecondsAttribute, "0")
      .addAttributesEntry(delaySecondsAttribute, "0")

    val fifoQueueUrl = client.createQueue(fifoQueue).getQueueUrl

    val messageRequest = new SendMessageRequest(fifoQueueUrl, "Message 1")
      .withMessageGroupId("group1")
      .withMessageDeduplicationId(UUID.randomUUID().toString)

    var longPollRequest = new ReceiveMessageRequest(fifoQueueUrl)
      .withMaxNumberOfMessages(1)
      .withAttributeNames("All")
      .withVisibilityTimeout(5)
      .withWaitTimeSeconds(20)
      .withReceiveRequestAttemptId(UUID.randomUUID().toString)

    var t = new Thread() {
      override def run(): Unit = {
        Thread.sleep(2100L)
        client.sendMessage(messageRequest)
      }
    }
    t.start()

    // When
    var start = System.currentTimeMillis()
    var messages = client.receiveMessage(longPollRequest).getMessages
    var end = System.currentTimeMillis()

    // Then
    (end - start) should be >= 2000L
    (end - start) should be <= 3000L
    messages.size should be(1)
  }

  test(
    "FIFO queues should not return a second message for the same message group if the first has not been deleted yet"
  ) {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val messages1 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages.asScala
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1").withMessageGroupId("group-1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2").withMessageGroupId("group-1"))

    val messages2 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages.asScala
    val messages3 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages.asScala
    client.deleteMessage(queueUrl, messages2.head.getReceiptHandle)
    val messages4 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).getMessages.asScala

    // Then
    messages1 should have size 0
    messages2.map(_.getBody).toSet should be(Set("Message 1"))
    messages3 should have size 0
    messages4.map(_.getBody).toSet should be(Set("Message 2"))
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
    client.sendMessage(
      new SendMessageRequest(queueUrl, group1).withMessageGroupId(group1).withMessageDeduplicationId("1")
    )
    client.sendMessage(
      new SendMessageRequest(queueUrl, group1).withMessageGroupId(group1).withMessageDeduplicationId("2")
    )
    client.sendMessage(
      new SendMessageRequest(queueUrl, group2).withMessageGroupId(group2).withMessageDeduplicationId("3")
    )
    client.sendMessage(
      new SendMessageRequest(queueUrl, group2).withMessageGroupId(group2).withMessageDeduplicationId("4")
    )

    val messages1 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.asScala
    val messages2 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.asScala

    // Then
    // When requesting 2 messages at a time, the first request should return 2 messages from group 1 (resp group 2) and
    // the second request should return 2 messages from group 2 (resp. group 1).
    messages1 should have size 2
    messages1.map(_.getBody).toSet should have size 1
    messages2 should have size 2
    messages2.map(_.getBody).toSet should have size 1
    messages1.map(_.getBody) should not be messages2.map(_.getBody)
  }

  test("FIFO queues should deliver messages in the same order as they are sent") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val messageBodies = for (i <- 1 to 20) yield s"Message $i"
    messageBodies.map(body => client.sendMessage(new SendMessageRequest(queueUrl, body).withMessageGroupId("group1")))

    // Then
    val deliveredSingleReceives = messageBodies.take(10).map { _ =>
      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.asScala
      client.deleteMessage(queueUrl, messages.head.getReceiptHandle)
      messages.head
    }
    // Messages received in a batch should be in order as well
    val batchReceive =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10)).getMessages.asScala

    val allMessages = deliveredSingleReceives ++ batchReceive
    allMessages.map(_.getBody) should be(messageBodies)
  }

  test("FIFO queues should deliver messages in the same order as they are sent in a batch") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val maxValidNumberOfMessagesInABatch = 10
    val messages = for (i <- 1 to maxValidNumberOfMessagesInABatch) yield {
      new SendMessageBatchRequestEntry()
        .withId(s"Id$i")
        .withMessageBody(s"Body$i")
        .withMessageDeduplicationId(s"DeduplicationId$i")
        .withMessageGroupId("messageGroupId")
    }

    client.sendMessageBatch(new SendMessageBatchRequest(queueUrl, messages.asJava))

    // Then
    val receivedMessages =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(maxValidNumberOfMessagesInABatch))
        .getMessages
        .asScala

    receivedMessages.map(_.getBody) should contain theSameElementsInOrderAs messages.map(_.getMessageBody)
  }

  test("FIFO queues should deliver the same messages for the same request attempt id") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val messageBodies = for (i <- 1 to 20) yield s"Message $i"
    messageBodies.map(body => client.sendMessage(new SendMessageRequest(queueUrl, body).withMessageGroupId("group1")))

    // Then
    val req = new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10).withReceiveRequestAttemptId("attempt1")
    val batch1 = client.receiveMessage(req).getMessages.asScala
    val batch2 = client.receiveMessage(req).getMessages.asScala
    batch1.map(_.getBody).toVector should be(messageBodies.take(10))
    batch2.map(_.getBody).toVector should be(messageBodies.take(10))

    // When a message gets modified (deleted or visibility time-out), the attempt id becomes invalid
    client.deleteMessage(queueUrl, batch2.head.getReceiptHandle)
    an[AmazonSQSException] shouldBe thrownBy {
      client.receiveMessage(req).getMessages
    }
  }

  test("FIFO queues should return an error if an invalid receive request attempt id parameter is provided") {
    // Given
    val fifoQueueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue.fifo")
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true")
      )
      .getQueueUrl
    val regularQueueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // An illegal character
    an[AmazonSQSException] shouldBe thrownBy {
      client.receiveMessage(new ReceiveMessageRequest(fifoQueueUrl).withReceiveRequestAttemptId("æ"))
    }

    // More than 128 characters
    val id = (for (_ <- 0 to 300) yield "1").mkString("")
    an[AmazonSQSException] shouldBe thrownBy {
      client.receiveMessage(new ReceiveMessageRequest(fifoQueueUrl).withReceiveRequestAttemptId(id))
    }

    // Regular queues don't allow message deduplication
    an[AmazonSQSException] shouldBe thrownBy {
      client.receiveMessage(new ReceiveMessageRequest(regularQueueUrl).withReceiveRequestAttemptId("attempt1"))
    }
  }

  test("FIFO queue messages should return FIFO attribute names") {
    val queueUrl = createFifoQueue()

    // When
    val messageGroupId = "myMessageGroupId"
    val deduplicationId = "myDedupId"
    for (i <- 0 to 10) {
      client.sendMessage(
        new SendMessageRequest(queueUrl, s"Message $i")
          .withMessageDeduplicationId(deduplicationId + i)
          .withMessageGroupId(messageGroupId + i)
      )
    }

    // Then
    val messages = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(2))
      .getMessages
      .asScala
    messages should have size 2
    messages.foreach { message =>
      message.getAttributes.get("MessageGroupId") should startWith(messageGroupId)
      message.getAttributes.get("MessageDeduplicationId") should startWith(deduplicationId)
    }

    // Specific attributes
    val withMessageGroupIdMessages = client
      .receiveMessage(
        new ReceiveMessageRequest(queueUrl).withAttributeNames("MessageGroupId").withMaxNumberOfMessages(2)
      )
      .getMessages
      .asScala
    withMessageGroupIdMessages should have size 2
    withMessageGroupIdMessages.foreach { message =>
      val attrs = message.getAttributes.asScala.toMap
      attrs("MessageGroupId") should startWith(messageGroupId)
      attrs.get("MessageDeduplicationId") should be(empty)
    }

    val withDedupIdMessages = client
      .receiveMessage(
        new ReceiveMessageRequest(queueUrl).withAttributeNames("MessageDeduplicationId").withMaxNumberOfMessages(2)
      )
      .getMessages
      .asScala
    withDedupIdMessages should have size 2
    withDedupIdMessages.foreach { message =>
      val attrs = message.getAttributes.asScala.toMap
      attrs.get("MessageGroupId") should be(empty)
      attrs("MessageDeduplicationId") should startWith(deduplicationId)
    }
  }

  test("FIFO queues should be purgable") {
    // Given
    val queueUrl = createFifoQueue()
    client.sendMessage(new SendMessageRequest(queueUrl, "Body 1").withMessageGroupId("1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "Body 2").withMessageGroupId("1"))
    val attributes1 =
      client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes
    val m1 = receiveSingleMessage(queueUrl)

    // When
    client.purgeQueue(new PurgeQueueRequest().withQueueUrl(queueUrl))
    val attributes2 =
      client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes
    client.sendMessage(new SendMessageRequest(queueUrl, "Body 3").withMessageGroupId("1"))
    val m2 = receiveSingleMessage(queueUrl)

    // Then
    m1 should be(Some("Body 1"))
    attributes1.get("ApproximateNumberOfMessages") should be("2")
    attributes2.get("ApproximateNumberOfMessages") should be("0")
    m2 should be(Some("Body 3"))
  }

  test("Message does not have SequenceNumber if it is not connected with FIFO queue") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val sendingResult = client.sendMessage(new SendMessageRequest(queueUrl, "Body 1"))
    val message = receiveSingleMessageObject(queueUrl).orNull

    Option(sendingResult.getSequenceNumber).isEmpty should be(true)
    message.getMessageAttributes.asScala.contains("SequenceNumber") should be(false)
  }

  test("Message should have SequenceNumber in result when sent to FIFO queue") {
    val groupId = "1"
    val queueUrl = createFifoQueue()

    val sendingResult = client.sendMessage(
      new SendMessageRequest(queueUrl, "Body 1")
        .withMessageGroupId(groupId)
    )

    Option(sendingResult.getSequenceNumber).isDefined should be(true)
  }

  test("FIFO queue - older message should have SequenceNumber which is less than that in newer message") {
    val groupId = "1"
    val queueUrl = createFifoQueue()

    val firstSeqNum =
      client.sendMessage(new SendMessageRequest(queueUrl, "Body 1").withMessageGroupId(groupId)).getSequenceNumber
    val secondSeqNum =
      client.sendMessage(new SendMessageRequest(queueUrl, "Body 2").withMessageGroupId(groupId)).getSequenceNumber

    firstSeqNum.toLong should be < secondSeqNum.toLong
  }

  test("FIFO queue - SequenceNumber continues to increase after deleting message from queue") {
    val groupId1 = "1"
    val queueUrl = createFifoQueue(attributes = Map(visibilityTimeoutAttribute -> "0"))

    val seqNumFromDeleted =
      client.sendMessage(new SendMessageRequest(queueUrl, "Body 1").withMessageGroupId(groupId1)).getSequenceNumber

    val willBeDeleted =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMessageAttributeNames("All")).getMessages.get(0)
    client.deleteMessage(new DeleteMessageRequest(queueUrl, willBeDeleted.getReceiptHandle))

    val secondSeqNum =
      client.sendMessage(new SendMessageRequest(queueUrl, "Body 2").withMessageGroupId(groupId1)).getSequenceNumber

    seqNumFromDeleted.toLong should be < secondSeqNum.toLong
  }

  test("FIFO queue - SequenceNumber is part of Attributes, but not MessageAttributes") {
    val groupId1 = "1"
    val queueUrl: String = createFifoQueue(attributes = Map(visibilityTimeoutAttribute -> "0"))

    client.sendMessage(new SendMessageRequest(queueUrl, "Body 1").withMessageGroupId(groupId1))

    val msg = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMessageAttributeNames("All"))
      .getMessages
      .get(0)

    val attributes = msg.getAttributes.asScala
    val msgAttributes = msg.getMessageAttributes.asScala

    attributes.contains("SequenceNumber") shouldBe true
    msgAttributes.contains("SequenceNumber") shouldBe false
  }

  test("FIFO queue - SequenceNumber is not incremented between receives") {
    val groupId1 = "1"
    val queueUrl = createFifoQueue(attributes = Map(visibilityTimeoutAttribute -> "0"))
    val res = client.sendMessage(new SendMessageRequest(queueUrl, "Body 1").withMessageGroupId(groupId1))

    val seqNum1 = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All"))
      .getMessages
      .get(0)
      .getAttributes
      .asScala("SequenceNumber")

    val seqNum2 = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All"))
      .getMessages
      .get(0)
      .getAttributes
      .asScala("SequenceNumber")

    res.getSequenceNumber should equal(seqNum1)
    res.getSequenceNumber should equal(seqNum2)
  }

  test("sendMessage should throw when message has more than 10 message attributes") {
    val sqsMessageAttributesLimit = 10

    val queueUrl = client.createQueue(new CreateQueueRequest("q")).getQueueUrl
    val attr = new MessageAttributeValue().withStringValue("str").withDataType("String")
    val atLeastElevenMessageAttributes = (1 to 11).map(_.toString -> attr).toMap.asJava

    atLeastElevenMessageAttributes.size() > sqsMessageAttributesLimit shouldBe true

    assertThrows[AmazonSQSException] {
      client.sendMessage(
        new SendMessageRequest(queueUrl, "MessageWithMoreThan10MessageAttributes")
          .withMessageAttributes(atLeastElevenMessageAttributes)
      )
    }
  }

  def queueVisibilityTimeout(queueUrl: String): Long = getQueueLongAttribute(queueUrl, visibilityTimeoutAttribute)

  test("should receive no more than the given amount of messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 10) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages

    // Then
    messages should have size 4
  }

  test("should receive less messages if no messages are available") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    for (i <- 1 to 9) client.sendMessage(new SendMessageRequest(queueUrl, "Message " + i))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(10)).getMessages

    // Then
    messages should have size 9
  }

  test("should send two messages in a batch") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val result = client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "Message 1"),
        new SendMessageBatchRequestEntry("2", "Message 2")
      )
    )

    // Then
    result.getSuccessful should have size 2
    result.getSuccessful.asScala.map(_.getId).toSet should be(Set("1", "2"))

    val messages =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages.asScala

    val bodies = messages.map(_.getBody).toSet
    bodies should be(Set("Message 1", "Message 2"))

    messages.map(_.getMessageId).toSet should be(result.getSuccessful.asScala.map(_.getMessageId).toSet)
  }

  test("should block message for the visibility timeout duration") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl

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
    val m1 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withVisibilityTimeout(2)).getMessages.get(0).getBody
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
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl

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

  test("should fail when deleting a message with invalid handle") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl

    // When
    val result = catching(classOf[AmazonSQSException]) either {
      client.deleteMessage(new DeleteMessageRequest(queueUrl, "0000#0000"))
    }

    result.isLeft should be(true)
    result.swap.map(_.getMessage).getOrElse("") should include("ReceiptHandleIsInvalid")
  }

  test("should delete messages in a batch") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl

    client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "Message 1"),
        new SendMessageBatchRequestEntry("2", "Message 2")
      )
    )

    val msgReceipts = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2))
      .getMessages
      .asScala
      .map(_.getReceiptHandle)

    // When
    val result = client.deleteMessageBatch(
      new DeleteMessageBatchRequest(queueUrl).withEntries(
        new DeleteMessageBatchRequestEntry("1", msgReceipts.head),
        new DeleteMessageBatchRequestEntry("2", msgReceipts(1)),
        new DeleteMessageBatchRequestEntry("3", "invalid")
      )
    )
    Thread.sleep(1100)

    val m = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.asScala.map(_.getId).toSet should be(Set("1", "2"))
    result.getFailed.asScala.map(_.getId).toSet should be(Set("3"))
    m should be(None) // messages deleted
  }

  test("should update message visibility timeout") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "5").asJava)
      )
      .getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.asScala.head
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
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    an[AmazonSQSException] shouldBe thrownBy {
      client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, "Unknown receipt handle", 1))
    }
  }

  test("should update message visibility timeout in a batch") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "5").asJava)
      )
      .getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 2")).getMessageId

    val msg1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.asScala.head
    val msg2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.asScala.head

    val result = client.changeMessageVisibilityBatch(
      new ChangeMessageVisibilityBatchRequest()
        .withQueueUrl(queueUrl)
        .withEntries(
          new ChangeMessageVisibilityBatchRequestEntry("1", msg1.getReceiptHandle).withVisibilityTimeout(1),
          new ChangeMessageVisibilityBatchRequestEntry("2", msg2.getReceiptHandle).withVisibilityTimeout(1),
          new ChangeMessageVisibilityBatchRequestEntry("3", "invalid").withVisibilityTimeout(1)
        )
    )

    // Messages should be already visible
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    val m4 = receiveSingleMessage(queueUrl)

    // Then
    result.getSuccessful.asScala.map(_.getId).toSet should be(Set("1", "2"))
    result.getFailed.asScala.map(e => (e.getId, e.getCode)) should be(List(("3", "ReceiptHandleIsInvalid")))

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
    val attributes =
      client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes

    // Then
    attributes.get("ApproximateNumberOfMessages") should be("2")
    attributes.get("ApproximateNumberOfMessagesNotVisible") should be("1")
    attributes.get("ApproximateNumberOfMessagesDelayed") should be("3")
    attributes should contain key "CreatedTimestamp"
    attributes should contain key "LastModifiedTimestamp"
    attributes should contain key visibilityTimeoutAttribute
    attributes should contain key delaySecondsAttribute
    attributes should contain key receiveMessageWaitTimeSecondsAttribute
    attributes should contain key "QueueArn"
  }

  test("should return proper queue statistics after receiving, deleting a message") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    def verifyQueueAttributes(expectedMsgs: Int, expectedNotVisible: Int, expectedDelayed: Int): Unit = {
      val attributes =
        client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes

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
      .getQueueAttributes(
        new GetQueueAttributesRequest(queueUrl).withAttributeNames(approximateNumberOfMessagesAttribute)
      )
      .getAttributes
      .get(approximateNumberOfMessagesAttribute)
      .toLong

    // Then
    approximateNumberOfMessages should be(1)
  }

  test("should receive message with statistics") {
    // Given
    val start = System.currentTimeMillis()
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val sentTimestampAttribute = "SentTimestamp"
    val approximateReceiveCountAttribute = "ApproximateReceiveCount"
    val approximateFirstReceiveTimestampAttribute = "ApproximateFirstReceiveTimestamp"

    def receiveMessages(): java.util.List[Message] = {
      client
        .receiveMessage(
          new ReceiveMessageRequest(queueUrl)
            .withAttributeNames(
              sentTimestampAttribute,
              approximateReceiveCountAttribute,
              approximateFirstReceiveTimestampAttribute
            )
        )
        .getMessages
    }

    // When
    val messageArray1 = receiveMessages()
    Thread.sleep(1100)
    val messageArray2 = receiveMessages()

    // Then
    messageArray1.size() should be(1)
    val sent1 = messageArray1.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent1 should be >= start
    messageArray1.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt should be(1)

    val approxReceive1 = messageArray1.get(0).getAttributes.get(approximateFirstReceiveTimestampAttribute).toLong
    approxReceive1 should be >= start

    messageArray2.size() should be(1)
    val sent2 = messageArray2.get(0).getAttributes.get(sentTimestampAttribute).toLong
    sent2 should be >= start
    messageArray2.get(0).getAttributes.get(approximateReceiveCountAttribute).toInt should be(2)
    messageArray2
      .get(0)
      .getAttributes
      .get(approximateFirstReceiveTimestampAttribute)
      .toLong should be >= approxReceive1

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
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1").withAttributes(Map(delaySecondsAttribute -> "1").asJava))
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
    client.setQueueAttributes(new SetQueueAttributesRequest(queueUrl, Map(delaySecondsAttribute -> "10").asJava))

    // Then
    queueDelay(queueUrl) should be(10)
  }

  test("should create queue with message wait") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1").withAttributes(Map(receiveMessageWaitTimeSecondsAttribute -> "1").asJava)
      )
      .getQueueUrl

    // When
    val start = System.currentTimeMillis()
    val m1 = receiveSingleMessage(queueUrl)
    val end = System.currentTimeMillis()

    // Then
    m1 should be(None)
    (end - start) should be >= 1000L
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
    client.setQueueAttributes(
      new SetQueueAttributesRequest(queueUrl, Map(receiveMessageWaitTimeSecondsAttribute -> "13").asJava)
    )

    // Then
    queueReceiveMessageWaitTimeSeconds(queueUrl) should be(13)
  }

  test("should receive message sent later when waiting for messages") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val t = new Thread() {
      override def run(): Unit = {
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
    (end - start) should be >= 500L
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
      client.sendMessageBatch(
        new SendMessageBatchRequest(queueUrl).withEntries(
          new SendMessageBatchRequestEntry("1", "Message 1"),
          new SendMessageBatchRequestEntry("1", "Message 2")
        )
      )
    }

    // Then
    result.isLeft should be(true)
  }

  test("should return an error if strict & sending too many messages in a batch") {
    strictOnlyShouldThrowException { cli =>
      // Given
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      // When
      cli.sendMessageBatch(
        new SendMessageBatchRequest(queueUrl).withEntries(
          (for (i <- 1 to 11) yield new SendMessageBatchRequestEntry(i.toString, "Message")): _*
        )
      )
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

  test("should return an error if strict & message body is empty") {
    strictOnlyShouldThrowException { cli =>
      val queueUrl = cli.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      cli.sendMessage(new SendMessageRequest(queueUrl, ""))
    }
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
      cli.sendMessageBatch(
        new SendMessageBatchRequest(queueUrl).withEntries(
          new SendMessageBatchRequestEntry("1", "x" * 140000),
          new SendMessageBatchRequestEntry("2", "x" * 140000)
        )
      )
    }
  }

  test(
    "should return a failure for one message, send another if strict & sending two messages in a batch, one with illegal characters"
  ) {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    val result = client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "OK"),
        new SendMessageBatchRequestEntry("2", "\u0000")
      )
    )

    // Then
    result.getSuccessful should have size 1
    result.getSuccessful.get(0).getId should be("1")

    result.getFailed should have size 1
    result.getFailed.get(0).getId should be("2")

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.asScala.map(_.getBody).toSet
    bodies should be(Set("OK"))
  }

  test(
    "should return a failure for one message, send another if strict & sending two messages in a batch, one with string attribute containing illegal characters"
  ) {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val builder = new StringBuilder
    builder.append(0x8.asInstanceOf[Char]).append(0xb.asInstanceOf[Char]).append(0xc.asInstanceOf[Char])
    val invalidString = builder.toString()

    // When
    val result = client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "OK")
          .withMessageAttributes(
            Map(
              "StringAttributeValue" -> new MessageAttributeValue()
                .withDataType("String")
                .withStringValue("valid")
            ).asJava
          ),
        new SendMessageBatchRequestEntry("2", "NG")
          .withMessageAttributes(
            Map(
              "StringAttributeValue" -> new MessageAttributeValue()
                .withDataType("String")
                .withStringValue(invalidString)
            ).asJava
          )
      )
    )

    // Then
    result.getSuccessful should have size 1
    result.getSuccessful.get(0).getId should be("1")

    result.getFailed should have size 1
    result.getFailed.get(0).getId should be("2")

    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    val bodies = messages.asScala.map(_.getBody).toSet
    bodies should be(Set("OK"))
  }

  test("should delete a message only if the most recent receipt handle is provided") {
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html
    // > When you use the DeleteMessage action, you must provide the most recently received ReceiptHandle for the
    // > message (otherwise, the request succeeds, but the message might not be deleted).
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1").asJava)
      )
      .getQueueUrl

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val m1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0) // 1st receive

    Thread.sleep(1100)
    client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.get(0) // 2nd receive

    val deleteResult = catching(classOf[AmazonServiceException]) either {
      client.deleteMessage(new DeleteMessageRequest(queueUrl, m1.getReceiptHandle)) // Shouldn't delete - old receipt
    }

    // Then
    Thread.sleep(1100)
    val m3 = receiveSingleMessage(queueUrl)
    m3 should be(Some("Message 1"))
    deleteResult.isLeft should be(true)
  }

  test("should return an error if creating an existing queue with a different visibility timeout") {
    val result = catching(classOf[AmazonServiceException]) either {
      // Given
      client
        .createQueue(
          new CreateQueueRequest("testQueue1")
            .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "10").asJava)
        )
        .getQueueUrl

      // When
      client
        .createQueue(
          new CreateQueueRequest("testQueue1")
            .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "14").asJava)
        )
        .getQueueUrl
    }

    result.isLeft should be(true)
  }

  test("should not return an error if creating an existing queue with a non default visibility timeout") {
    // Given
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "42").asJava)
      )
      .getQueueUrl

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
    val attributes =
      client.getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("All")).getAttributes
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
    val messages = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(4))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < 2500L
  }

  test("if message visibility changes during a long pool, should receive messages as soon as they become available") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val handle = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withVisibilityTimeout(4))
      .getMessages
      .get(0)
      .getReceiptHandle

    new Thread {
      override def run(): Unit = {
        Thread.sleep(1000L)
        client.changeMessageVisibility(new ChangeMessageVisibilityRequest(queueUrl, handle, 1))
      }
    }.start()

    val start = System.currentTimeMillis()
    val messages = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(5))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < 2500L // 1 second waiting with the old visibility, then change to 1 second
  }

  test("should allow 0 as a value for message wait time seconds") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    // When
    val start = System.currentTimeMillis()
    val messages = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(0))
      .getMessages
    val end = System.currentTimeMillis()

    // Then
    messages.size should be(1)
    (end - start) should be < 1000L
  }

  test("should send & receive the same message id") {
    // Given
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    // When
    client.purgeQueue(new PurgeQueueRequest(queueUrl))
    val msgIdSent = client.sendMessage(new SendMessageRequest(queueUrl, "Message 1")).getMessageId
    val msgIdReceived = client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withWaitTimeSeconds(0))
      .getMessages
      .get(0)
      .getMessageId

    // Then
    msgIdSent should be(msgIdReceived)
  }

  test("should create queue with redrive policy.") {

    // Given
    val deadLetterQueueUrl = client.createQueue(new CreateQueueRequest("dlq1")).getQueueUrl
    val deadLetterQueueAttributes = client.getQueueAttributes(deadLetterQueueUrl, List("All").asJava).getAttributes

    // When
    val redrivePolicy = RedrivePolicy("dlq1", awsRegion, awsAccountId, 1).toJson.toString

    val createQueueResult = client.createQueue(
      new CreateQueueRequest("q1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1", redrivePolicyAttribute -> redrivePolicy).asJava)
    )
    val newQueueAttributes =
      client.getQueueAttributes(createQueueResult.getQueueUrl, List("All").asJava).getAttributes.asScala

    // Then
    deadLetterQueueAttributes.keySet() should not contain redrivePolicyAttribute
    newQueueAttributes.keys should contain(redrivePolicyAttribute)
    newQueueAttributes(redrivePolicyAttribute) should be(redrivePolicy)
  }

  test(
    "When message is moved to dead letter queue, its SentTimestamp and ApproximateFirstReceiveTimestamp attributes should not be changed"
  ) {

    def getOneMessage(queueUrl: String) = {
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(2).withAttributeNames("All"))
        .getMessages
        .asScala
        .headOption
        .orNull
    }

    // Given
    val deadLetterQueueUrl = client.createQueue(new CreateQueueRequest("dlq1")).getQueueUrl
    val redrivePolicy = RedrivePolicy("dlq1", awsRegion, awsAccountId, 2).toJson.toString
    val createQueueResult = client.createQueue(
      new CreateQueueRequest("q1")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1", redrivePolicyAttribute -> redrivePolicy).asJava)
    )

    // When

    client.sendMessage(new SendMessageRequest(createQueueResult.getQueueUrl, "test message"))

    val msg1 = getOneMessage(createQueueResult.getQueueUrl)
    val msg2 = getOneMessage(createQueueResult.getQueueUrl)
    val emptyMsg = getOneMessage(createQueueResult.getQueueUrl)

    val msgFromDeadLetterQueue = getOneMessage(deadLetterQueueUrl)

    // Then
    val msg1SentTimestamp = msg1.getAttributes.get("SentTimestamp")
    val msg1ApproximateReceiveCount = msg1.getAttributes.get("ApproximateReceiveCount")
    val msg1ApproximateFirstReceiveTimestamp = msg1.getAttributes.get("SentTimestamp")

    val msg2SentTimestamp = msg2.getAttributes.get("SentTimestamp")
    val msg2ApproximateReceiveCount = msg2.getAttributes.get("ApproximateReceiveCount")
    val msg2ApproximateFirstReceiveTimestamp = msg2.getAttributes.get("SentTimestamp")

    val msgFromDeadLetterQueueSentTimestamp = msgFromDeadLetterQueue.getAttributes.get("SentTimestamp")
    val msgFromDeadLetterQueueApproximateReceiveCount =
      msgFromDeadLetterQueue.getAttributes.get("ApproximateReceiveCount")
    val msgFromDeadLetterQueueApproximateFirstReceiveTimestamp =
      msgFromDeadLetterQueue.getAttributes.get("SentTimestamp")

    msg1SentTimestamp shouldBe msg2SentTimestamp
    msg2SentTimestamp shouldBe msgFromDeadLetterQueueSentTimestamp

    msg2ApproximateReceiveCount.toInt shouldBe (msg1ApproximateReceiveCount.toInt + 1)
    msgFromDeadLetterQueueApproximateReceiveCount.toInt shouldBe (msg2ApproximateReceiveCount.toInt + 1)

    msg1ApproximateFirstReceiveTimestamp shouldBe msg2ApproximateFirstReceiveTimestamp
    msg2ApproximateFirstReceiveTimestamp shouldBe msgFromDeadLetterQueueApproximateFirstReceiveTimestamp

    emptyMsg shouldBe null
  }

  test("should return an error when the deadletter queue does not exist") {

    // Given no dead letter queue
    // Then
    a[QueueDoesNotExistException] shouldBe thrownBy {
      client.createQueue(
        new CreateQueueRequest("q1").withAttributes(
          Map(
            redrivePolicyAttribute -> RedrivePolicy("queueDoesNotExist", awsRegion, awsAccountId, 1).toJson.toString
          ).asJava
        )
      )
    }
  }

  test("should validate redrive policy json") {
    // Then
    a[AmazonSQSException] shouldBe thrownBy {
      client.createQueue(
        new CreateQueueRequest("q1")
          .withAttributes(Map(redrivePolicyAttribute -> "not a proper json policy").asJava)
      )
    }
    a[AmazonSQSException] shouldBe thrownBy {
      client.createQueue(
        new CreateQueueRequest("q1")
          .withAttributes(Map(redrivePolicyAttribute -> """{"wrong": "json"}""").asJava)
      )
    }
  }

  test("should throw an error when trying to recreate a queue with different attributes") {
    client
      .createQueue(
        new CreateQueueRequest("attributeTestQueue")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "3").asJava)
      )

    a[AmazonSQSException] shouldBe thrownBy {
      client
        .createQueue(
          new CreateQueueRequest("attributeTestQueue")
            .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "2").asJava)
        )
    }
  }

  test("should return queue url when trying to recreate a queue with same attributes") {
    val firstQueueUrl = client
      .createQueue(
        new CreateQueueRequest("attributeTestQueue2")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "3").asJava)
      )
      .getQueueUrl

    val secondQueueUrl = client
      .createQueue(
        new CreateQueueRequest("attributeTestQueue2")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "3").asJava)
      )
      .getQueueUrl

    secondQueueUrl shouldBe firstQueueUrl
  }

  test("should tag the queue") {
    val tags = Map("tag1" -> "value1", "tag2" -> "value2")
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("tagTestQueue")
      )
      .getQueueUrl

    client.tagQueue(new TagQueueRequest().withQueueUrl(queueUrl).withTags(tags.asJava))

    val readTags = client.listQueueTags(queueUrl)

    readTags.getTags.asScala.toMap shouldBe tags
  }

  test("should update and add tags to the queue") {
    val tags = Map("tag1" -> "value1", "tag2" -> "value2")
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("tagTestQueue2")
      )
      .getQueueUrl

    client.tagQueue(new TagQueueRequest().withQueueUrl(queueUrl).withTags(tags.asJava))

    val updatedTags = Map("tag2" -> "newValue2", "tag3" -> "value3")

    client.tagQueue(new TagQueueRequest().withQueueUrl(queueUrl).withTags(updatedTags.asJava))

    val allTagsOnQueue = Map("tag1" -> "value1", "tag2" -> "newValue2", "tag3" -> "value3")

    val readUpdatedTags = client.listQueueTags(queueUrl)

    readUpdatedTags.getTags.asScala.toMap shouldBe allTagsOnQueue
  }

  test("should remove tags from the queue") {
    val tags = Map("tag1" -> "value1", "tag2" -> "newValue2", "tag3" -> "value3")
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("tagTestQueue3")
      )
      .getQueueUrl

    client.tagQueue(new TagQueueRequest().withQueueUrl(queueUrl).withTags(tags.asJava))

    client.untagQueue(new UntagQueueRequest().withQueueUrl(queueUrl).withTagKeys("tag1"))

    val readTags = client.listQueueTags(queueUrl)

    val newTags = tags - "tag1"

    readTags.getTags.asScala.toMap shouldBe newTags
  }

  test("should remove multiple tags from the queue") {
    val tags = Map("tag1" -> "value1", "tag2" -> "newValue2", "tag3" -> "value3")
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("tagTestQueue3")
      )
      .getQueueUrl

    client.tagQueue(new TagQueueRequest().withQueueUrl(queueUrl).withTags(tags.asJava))

    client.untagQueue(new UntagQueueRequest().withQueueUrl(queueUrl).withTagKeys(List("tag1", "tag2").asJava))

    val readTags = client.listQueueTags(queueUrl)

    val newTags = tags - "tag1" - "tag2"

    readTags.getTags.asScala.toMap shouldBe newTags
  }

  test("should update queue redrive policy.") {

    val deadLetterQueueUrl = client.createQueue(new CreateQueueRequest("dlq2")).getQueueUrl
    val deadLetterQueueAttributes = client.getQueueAttributes(deadLetterQueueUrl, List("All").asJava).getAttributes
    val redrivePolicy = RedrivePolicy("dlq2", awsRegion, awsAccountId, 1).toJson.toString
    val createQueueResult = client.createQueue(
      new CreateQueueRequest("q2")
        .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1", redrivePolicyAttribute -> redrivePolicy).asJava)
    )
    val newQueueAttributes =
      client.getQueueAttributes(createQueueResult.getQueueUrl, List("All").asJava).getAttributes.asScala

    deadLetterQueueAttributes.keySet() should not contain redrivePolicyAttribute
    newQueueAttributes.keys should contain(redrivePolicyAttribute)
    newQueueAttributes(redrivePolicyAttribute) should be(redrivePolicy)

    client.createQueue(new CreateQueueRequest("dlq2a"))
    val newRedrivePolicy = RedrivePolicy("dlq2a", awsRegion, awsAccountId, 1).toJson.toString
    client.setQueueAttributes(
      new SetQueueAttributesRequest()
        .withQueueUrl(createQueueResult.getQueueUrl)
        .withAttributes(Map(redrivePolicyAttribute -> newRedrivePolicy).asJava)
    )
    val updatedQueueAttributes =
      client.getQueueAttributes(createQueueResult.getQueueUrl, List("All").asJava).getAttributes.asScala

    updatedQueueAttributes.keys should contain(redrivePolicyAttribute)
    updatedQueueAttributes(redrivePolicyAttribute) should be(newRedrivePolicy)
  }

  test("should not create queue with redrive policy if dlq does not exist") {

    val redrivePolicy = RedrivePolicy("notaqueue", awsRegion, awsAccountId, 1).toJson.toString
    a[AmazonSQSException] shouldBe thrownBy {
      client.createQueue(
        new CreateQueueRequest("q3")
          .withAttributes(Map(defaultVisibilityTimeoutAttribute -> "1", redrivePolicyAttribute -> redrivePolicy).asJava)
      )
    }
  }

  test("should not update queue redrive policy if dlq does not exist") {

    val createQueueResult = client.createQueue(new CreateQueueRequest("q4"))

    val redrivePolicy = RedrivePolicy("notaqueue", awsRegion, awsAccountId, 1).toJson.toString
    a[AmazonSQSException] shouldBe thrownBy {
      client.setQueueAttributes(
        new SetQueueAttributesRequest()
          .withQueueUrl(createQueueResult.getQueueUrl)
          .withAttributes(Map(redrivePolicyAttribute -> redrivePolicy).asJava)
      )
    }
  }

  test("should move message to dlq after exceeding maxReceiveCount") {

    // given
    val messageBody = "Message 1"
    val deadLetterQueueUrl = client.createQueue(new CreateQueueRequest("testDlq")).getQueueUrl
    val redrivePolicy = RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.toString

    val createQueueResult = client
      .createQueue(
        new CreateQueueRequest("main")
          .withAttributes(
            Map(defaultVisibilityTimeoutAttribute -> "10", redrivePolicyAttribute -> redrivePolicy).asJava
          )
      )
      .getQueueUrl

    // when
    client.sendMessage(createQueueResult, messageBody)
    val firstReceiveResult =
      client.receiveMessage(new ReceiveMessageRequest().withQueueUrl(createQueueResult).withWaitTimeSeconds(11))
    val secondReceiveResult =
      client.receiveMessage(new ReceiveMessageRequest().withQueueUrl(createQueueResult).withWaitTimeSeconds(11))
    val messageFromDlq = client.receiveMessage(deadLetterQueueUrl)

    // then
    messageFromDlq.getMessages.asScala.map(_.getBody) shouldBe firstReceiveResult.getMessages.asScala.map(_.getBody)
    messageFromDlq.getMessages.asScala.map(_.getBody) should contain(messageBody)
    secondReceiveResult.getMessages shouldBe empty
  }

  test("should list all source queues for a dlq") {
    // given
    val dlqUrl = client.createQueue(new CreateQueueRequest("testDlq")).getQueueUrl
    val redrivePolicy = RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.toString

    val qUrls = for (i <- 1 to 3) yield {
      client
        .createQueue(
          new CreateQueueRequest("q" + i)
            .withAttributes(
              Map(redrivePolicyAttribute -> redrivePolicy).asJava
            )
        )
        .getQueueUrl
    }

    // when
    val request = new ListDeadLetterSourceQueuesRequest(dlqUrl)
    val result = client.listDeadLetterSourceQueues(request).getQueueUrls.asScala

    // then
    result should contain theSameElementsAs (qUrls)
  }

  def queueDelay(queueUrl: String): Long = getQueueLongAttribute(queueUrl, delaySecondsAttribute)

  def getQueueLongAttribute(queueUrl: String, attributeName: String): Long = {
    client
      .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames(attributeName))
      .getAttributes
      .get(attributeName)
      .toLong
  }

  def queueReceiveMessageWaitTimeSeconds(queueUrl: String): Long =
    getQueueLongAttribute(queueUrl, receiveMessageWaitTimeSecondsAttribute)

  def strictOnlyShouldThrowException(body: AmazonSQS => Unit): Unit = {
    // When
    val resultStrict = catching(classOf[AmazonServiceException]) either body(client)
    val resultRelaxed = catching(classOf[AmazonServiceException]) either body(relaxedClient)

    // Then
    resultStrict.isLeft should be(true)
    resultRelaxed.isRight should be(true)
  }

  def assertInvalidParameterException(parameterName: String)(f: => Any): Unit = {
    val ex = the[AmazonSQSException] thrownBy f
    ex.getErrorCode should be("InvalidParameterValue")
    ex.getMessage should include(parameterName)
  }

  def assertInvalidParameterQueueTypeException(parameterName: String)(f: => Any): Unit = {
    val ex = the[AmazonSQSException] thrownBy f
    ex.getErrorCode should be("InvalidParameterValue")
    ex.getMessage should include(parameterName)
    ex.getMessage should include("The request include parameter that is not valid for this queue type")
  }

  def assertMissingParameterException(parameterName: String)(f: => Any): Unit = {
    val ex = the[AmazonSQSException] thrownBy f
    ex.getErrorCode should be("MissingParameter")
    ex.getMessage should include(s"The request must contain the parameter $parameterName.")
  }

  def appendRange(builder: StringBuilder, start: Int, end: Int): Unit = {
    var current = start
    while (current <= end) {
      builder.appendAll(Character.toChars(current))
      current += 1
    }
  }

  private def createFifoQueue(suffix: Int = 1, attributes: Map[String, String] = Map.empty): String = {
    val createRequest1 = new CreateQueueRequest(s"testFifoQueue$suffix.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "true")
    val createRequest2 = attributes.foldLeft(createRequest1) { case (acc, (k, v)) => acc.addAttributesEntry(k, v) }
    client.createQueue(createRequest2).getQueueUrl
  }
}
