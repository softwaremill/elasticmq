package org.elasticmq.rest.sqs

import org.elasticmq.StringMessageAttribute
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.scalatest.{Inside, LoneElement, OptionValues, Tag}
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import spray.json.DefaultJsonProtocol.listFormat

import scala.sys.process._
import scala.language.postfixOps
import spray.json._

import scala.collection.mutable

object Only213 extends Tag("org.elasticmq.rest.sqs.Only213")
class AmazonCliTestSuite
    extends SqsClientServerCommunication
    with Matchers
    with TableDrivenPropertyChecks
    with OptionValues
    with Inside
    with LoneElement {

  val cliVersions = Table(
    "cli version",
    AWSCli(Option(System.getenv("AWS_CLI_V1_EXECUTABLE")).getOrElse("aws1"), "aws version 1"),
    AWSCli(Option(System.getenv("AWS_CLI_V2_EXECUTABLE")).getOrElse("aws"), "aws version 2")
  )

  def createQueue(name: String, attributesJson: Option[String] = None)(implicit cli: AWSCli): CreateQueueResponse = {
    val attributesOption = attributesJson.fold("")(json => s"--attributes='$json'")
    val result =
      s"""${cli.executable} sqs create-queue --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-name=$name $attributesOption""" !!

    result.parseJson.convertTo[CreateQueueResponse]
  }

  def tag(url: String, tags: String)(implicit cli: AWSCli): Unit =
    s"""${cli.executable} sqs tag-queue --tags="$tags" --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!

  def untag(url: String, tags: String)(implicit cli: AWSCli): Unit =
    s"""${cli.executable} sqs untag-queue --tag-keys="$tags" --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!

  def listTags(url: String)(implicit cli: AWSCli): ListQueueTagsResponse = {
    val result =
      s"""${cli.executable} sqs list-queue-tags --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!

    result.parseJson.convertTo[ListQueueTagsResponse]
  }

  def deleteQueue(url: String)(implicit cli: AWSCli): Unit = {
    s"""${cli.executable} sqs delete-queue --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url """ !!
  }

  def getQueueUrl(name: String)(implicit cli: AWSCli): GetQueueURLResponse = {
    val result =
      s"""${cli.executable} sqs get-queue-url --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-name=$name""" !!

    result.parseJson.convertTo[GetQueueURLResponse]
  }

  def listQueues(prefix: Option[String] = None)(implicit cli: AWSCli): ListQueuesResponse = {
    val prefixStr = prefix.fold("")(s => s"--queue-name-prefix=$s")

    val result =
      s"""${cli.executable} sqs list-queues $prefixStr --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request""" !!

    if (result.nonEmpty) {
      result.parseJson.convertTo[ListQueuesResponse]
    } else {
      ListQueuesResponse(List.empty)
    }
  }

  def sendMessage(messageBody: String, queueUrl: String)(implicit cli: AWSCli): SendMessageResponse = {
    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  def sendMessageWithAttributes(
      messageBody: String,
      queueUrl: String,
      messageAttributes: String
  )(implicit cli: AWSCli): SendMessageResponse = {
    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody --message-attributes='$messageAttributes'""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  def sendMessage(
      messageBody: String,
      queueUrl: String,
      messageAttributes: Option[String],
      systemAttributes: Option[String]
  )(implicit cli: AWSCli): SendMessageResponse = {
    val messageAttributesStr = messageAttributes.fold("")(v => s"--message-attributes='$v'")
    val systemAttributesStr = systemAttributes.fold("")(v => s"--message-system-attributes='$v'")

    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody $messageAttributesStr $systemAttributesStr""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  def sendMessageWithAttributesAndSystemAttributes(
      messageBody: String,
      queueUrl: String,
      messageAttributes: String,
      systemAttributes: String
  )(implicit cli: AWSCli): SendMessageResponse =
    sendMessage(messageBody, queueUrl, Some(messageAttributes), Some(systemAttributes))

  def receiveMessage(
      queueUrl: String,
      attributeNames: String = "All",
      messageAttributeNames: String = "All",
      maxNumberOfMessages: String = "10"
  )(implicit
      cli: AWSCli
  ): ReceiveMessageResponse = {
    val attributeNamesStr = s"--attribute-names='$attributeNames'"
    val messageAttributeNamesStr = s"--message-attribute-names='$messageAttributeNames'"
    val maxNumberOfMessagesStr = s"--max-number-of-messages='$maxNumberOfMessages'"

    val result =
      s"""${cli.executable} sqs receive-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl $attributeNamesStr $messageAttributeNamesStr $maxNumberOfMessagesStr""" !!

    result.parseJson.convertTo[ReceiveMessageResponse]
  }

  def sendMessageBatch(queueUrl: String, entries: String)(implicit
      cli: AWSCli
  ): BatchResponse[BatchMessageSendResponseEntry] = {
    val result =
      s"""${cli.executable} sqs send-message-batch --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl  --entries='[$entries]'""" !!

    result.parseJson.convertTo[BatchResponse[BatchMessageSendResponseEntry]]
  }

  def deleteMessage(queueUrl: String, receiptHandle: String)(implicit cli: AWSCli): Unit = {
    s"""${cli.executable} sqs delete-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --receipt-handle="$receiptHandle" """ !!
  }

  def deleteMessageBatch(queueUrl: String, entries: String)(implicit
      cli: AWSCli
  ): List[BatchDeleteMessageResponseEntry] = {
    val result =
      s"""${cli.executable} sqs delete-message-batch --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --entries='[$entries]'""" !!

    result.parseJson.asJsObject.fields
      .getOrElse("Successful", fail("couldn't find successful field"))
      .convertTo[List[BatchDeleteMessageResponseEntry]]
  }

  forAll(cliVersions) { implicit version =>
    test(s"should create a queue and get queue url ${version.name}") {

      val create = createQueue("test-queue")

      val get = getQueueUrl("test-queue")

      get.QueueUrl shouldBe create.QueueUrl
    }

    test(s"should list created queues ${version.name}") {
      createQueue("test-queue1")
      createQueue("test-queue2")

      val get = listQueues()

      get.QueueUrls should contain allOf (s"$ServiceEndpoint/$awsAccountId/test-queue2", s"$ServiceEndpoint/$awsAccountId/test-queue1")
    }

    test(s"should list queues with the specified prefix ${version.name}") {
      // Given
      createQueue("aaa-test-queue1")
      createQueue("aaa-test-queue2")
      createQueue("bbb-test-queue2")

      // When
      val get = listQueues(prefix = Some("aaa"))

      get.QueueUrls.length shouldBe 2
      get.QueueUrls should contain allOf (s"$ServiceEndpoint/$awsAccountId/aaa-test-queue2", s"$ServiceEndpoint/$awsAccountId/aaa-test-queue1")
    }

    test(s"should send a single message to queue ${version.name}", Only213) {
      // given
      createQueue("test-queue")
      val queueUrl = getQueueUrl("test-queue").QueueUrl

      // when
      val message = sendMessage("simpleMessage", queueUrl)

      // then
      message.MessageId.isEmpty shouldBe false
      message.MD5OfMessageBody.isEmpty shouldBe false
      message.MD5OfMessageAttributes shouldBe empty
      message.MD5OfMessageSystemAttributes shouldBe empty
      message.SequenceNumber shouldBe empty
    }

    test(s"should send a single message with Attributes to queue ${version.name}", Only213) {
      // given
      createQueue("test-queue")
      val queueUrl = getQueueUrl("test-queue").QueueUrl
      val messageAttributes = """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world" } }"""

      // when
      val message = sendMessageWithAttributes("simpleMessage", queueUrl, messageAttributes)

      // then
      message.MessageId.isEmpty shouldBe false
      message.MD5OfMessageBody.isEmpty shouldBe false
      message.MD5OfMessageAttributes.isEmpty shouldBe false
      message.MD5OfMessageSystemAttributes shouldBe empty
      message.SequenceNumber shouldBe empty
    }

    test(s"should send a single message with Attributes and System Attributes to queue ${version.name}", Only213) {
      // given
      createQueue("test-queue")
      val queueUrl = getQueueUrl("test-queue").QueueUrl
      val messageAttributes = """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world" } }"""
      val systemAttributes =
        """{ "AWSTraceHeader": { "DataType": "String", "StringValue": "your-xray-trace-header-string" } }"""

      // when
      val message =
        sendMessageWithAttributesAndSystemAttributes("simpleMessage", queueUrl, messageAttributes, systemAttributes)

      // then
      message.MessageId.isEmpty shouldBe false
      message.MD5OfMessageBody.isEmpty shouldBe false
      message.MD5OfMessageAttributes.isEmpty shouldBe false
      message.MD5OfMessageSystemAttributes.isEmpty shouldBe false
      message.SequenceNumber shouldBe empty
    }

    test(s"should fail if message is sent to missing queue ${version.name}", Only213) {
      // given
      val outLines = mutable.ListBuffer.empty[String]
      val errLines = mutable.ListBuffer.empty[String]

      val logger = new ProcessLogger {
        override def out(s: => String): Unit = outLines.append(s)

        override def err(s: => String): Unit = errLines.append(s)

        override def buffer[T](f: => T): T = f
      }

      // when
      val queueUrl = s"$ServiceEndpoint/$awsAccountId/miss"

      intercept[Exception] {
        s"""${version.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --message-body=hello --queue-url=$queueUrl""" !! (logger)
      }

      // then
      outLines.mkString("\n") shouldBe empty
      errLines.mkString("\n") should include("AWS.SimpleQueueService.NonExistentQueue; see the SQS docs.")
    }

    test(s"should tag, untag and list queue tags ${version.name}", Only213) {
      // given
      val url = createQueue("test-queue").QueueUrl

      // when
      tag(url, "a='A', b='B'")

      // then
      listTags(url).Tags shouldBe Map("a" -> "A", "b" -> "B")

      // when
      untag(url, "b")

      // then
      listTags(url).Tags shouldBe Map("a" -> "A")

      deleteQueue(url)
    }

    test(s"should add permission ${version.name}") {
      // given
      val url = createQueue("permission-test").QueueUrl

      // when ~> then
      s"""${version.executable} sqs add-permission --label l --aws-account-ids=$awsAccountId --actions=get --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!
    }

    test(s"should remove permission ${version.name}", Only213) {
      // given
      val url = createQueue("permission-test").QueueUrl
      s"""${version.executable} sqs add-permission --label l --aws-account-ids=$awsAccountId --actions=get --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !

      // when ~> then
      s"""${version.executable} sqs remove-permission --label l --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!
    }

    test(s"should purge queue ${version.name}") {
      // given
      val url = createQueue("permission-test").QueueUrl

      // when ~> then
      s"""${version.executable} sqs purge-queue --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$url""" !!
    }

    test(s"should delete created queue with ${version.name}") {
      // given
      val queue = createQueue("test-queue")
      val listQueuesBeforeDelete = listQueues()
      listQueuesBeforeDelete.QueueUrls.size shouldBe 1

      // when
      deleteQueue(queue.QueueUrl)
      val listQueuesAfterDelete = listQueues()

      // then
      listQueuesAfterDelete.QueueUrls.size shouldBe 0
    }

    test(s"should receive message with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val firstMessageBody = "simpleMessageOne"
      val secondMessageBody = "simpleMessageTwo"
      val firstMessageAttributes =
        """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world one" } }"""
      val secondMessageAttributes =
        """{ "secondAttribute": { "DataType": "String", "StringValue": "hello world two" } }"""
      val firstMessage = sendMessageWithAttributes(firstMessageBody, queue.QueueUrl, firstMessageAttributes)
      val secondMessage = sendMessageWithAttributes(secondMessageBody, queue.QueueUrl, secondMessageAttributes)

      // when
      val receivedMessage = receiveMessage(queue.QueueUrl, "All", "All", "2")

      // then
      receivedMessage.Messages.size shouldBe 2
      inside(receivedMessage.Messages.head) { case msg =>
        import msg._
        MessageId shouldBe firstMessage.MessageId
        ReceiptHandle.nonEmpty shouldBe true
        MD5OfBody shouldBe firstMessage.MD5OfMessageBody
        Body shouldBe firstMessageBody
        Attributes.size shouldBe 4
        Attributes.get("SentTimestamp").map(v => v.nonEmpty shouldBe true)
        Attributes.get("ApproximateReceiveCount").map(_ shouldBe "1")
        Attributes.get("ApproximateFirstReceiveTimestamp").map(v => v.nonEmpty shouldBe true)
        Attributes.get("SenderId").map(_ shouldBe "127.0.0.1")
        MD5OfMessageAttributes.nonEmpty shouldBe true
        MessageAttributes.nonEmpty shouldBe true
        MessageAttributes.get("firstAttribute").map { msgAtt =>
          msgAtt.getDataType() shouldBe "String"
          msgAtt shouldBe a[StringMessageAttribute]
          inside(msgAtt) { case stringMessageAttribute: StringMessageAttribute =>
            stringMessageAttribute.stringValue shouldBe "hello world one"
          }
        }
      }
      inside(receivedMessage.Messages(1)) { case msg =>
        import msg._
        MessageId shouldBe secondMessage.MessageId
        ReceiptHandle.nonEmpty shouldBe true
        MD5OfBody shouldBe secondMessage.MD5OfMessageBody
        Body shouldBe secondMessageBody
        Attributes.size shouldBe 4
        Attributes.get("SentTimestamp").map(v => v.nonEmpty shouldBe true)
        Attributes.get("ApproximateReceiveCount").map(_ shouldBe "1")
        Attributes.get("ApproximateFirstReceiveTimestamp").map(v => v.nonEmpty shouldBe true)
        Attributes.get("SenderId").map(_ shouldBe "127.0.0.1")
        MD5OfMessageAttributes.nonEmpty shouldBe true
        MessageAttributes.nonEmpty shouldBe true
        MessageAttributes.get("secondAttribute").map { msgAtt =>
          msgAtt.getDataType() shouldBe "String"
          msgAtt shouldBe a[StringMessageAttribute]
          inside(msgAtt) { case stringMessageAttribute: StringMessageAttribute =>
            stringMessageAttribute.stringValue shouldBe "hello world two"
          }
        }
      }
    }

    test(s"should send message batch with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val firstMessageBody = "messageOne"
      val entries = s"""{"Id": "1", "MessageBody": "$firstMessageBody"}, {"Id": "2", "MessageBody": ""}"""

      // when
      val batchMessages = sendMessageBatch(queue.QueueUrl, entries)

      // then
      batchMessages.Failed.size shouldBe 1
      inside(batchMessages.Failed.head) { case failedMessage =>
        import failedMessage._
        Id shouldBe "2"
        SenderFault shouldBe true
        Code shouldBe "The request must contain the parameter MessageBody."
        Message shouldBe "The request must contain the parameter MessageBody.; see the SQS docs."
      }

      batchMessages.Successful.size shouldBe 1
      inside(batchMessages.Successful.head) { case successfulMessage =>
        import successfulMessage._
        Id shouldBe "1"
        MessageId.nonEmpty shouldBe true
        MD5OfMessageBody.nonEmpty shouldBe true
      }
    }

    test(s"should delete message with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val messageAttributes =
        """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world one" } }"""
      sendMessageWithAttributes("simpleMessage", queue.QueueUrl, messageAttributes)
      val receivedMessage = receiveMessage(queue.QueueUrl)
      val receiptHandle = receivedMessage.Messages.head.ReceiptHandle

      // when
      // then
      deleteMessage(queue.QueueUrl, receiptHandle)
    }

    test(s"should delete message batch with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val firstMessageBody = "simpleMessageOne"
      val secondMessageBody = "simpleMessageTwo"
      val firstMessageAttributes =
        """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world one" } }"""
      val secondMessageAttributes =
        """{ "secondAttribute": { "DataType": "String", "StringValue": "hello world two" } }"""
      val firstMessage = sendMessageWithAttributes(firstMessageBody, queue.QueueUrl, firstMessageAttributes)
      val secondMessage = sendMessageWithAttributes(secondMessageBody, queue.QueueUrl, secondMessageAttributes)

      val receivedMessages = receiveMessage(queue.QueueUrl)
      val receiptHandles = receivedMessages.Messages.map(msg => msg.MessageId -> msg.ReceiptHandle).toMap
      val entries = receiptHandles
        .map { case (id, receiptHandle) =>
          s"""{"Id": "$id", "ReceiptHandle": "$receiptHandle"}"""
        }
        .mkString(", ")

      // when
      val deleteMessageBatchEntries = deleteMessageBatch(queue.QueueUrl, entries)

      // then
      deleteMessageBatchEntries.size shouldBe 2
      inside(deleteMessageBatchEntries.head) { case deletedMessage =>
        deletedMessage.Id shouldBe firstMessage.MessageId
      }
      inside(deleteMessageBatchEntries(1)) { case deletedMessage =>
        deletedMessage.Id shouldBe secondMessage.MessageId
      }
    }

    test(s"change message visibility with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      sendMessageWithAttributes(
        "hello",
        queue.QueueUrl,
        """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world one" } }"""
      )

      val msg = receiveMessage(queue.QueueUrl).Messages.loneElement

      // when ~> then
      s"""${version.executable} sqs change-message-visibility --visibility-timeout=100 --receipt-handle=${msg.ReceiptHandle} --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=${queue.QueueUrl}""" !!
    }

    test(s"change message visibility batch with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val attrs = """{ "firstAttribute": { "DataType": "String", "StringValue": "hello world one" } }"""
      sendMessageWithAttributes(
        "hello1",
        queue.QueueUrl,
        attrs
      )
      sendMessageWithAttributes(
        "hello2",
        queue.QueueUrl,
        attrs
      )
      sendMessageWithAttributes(
        "hello3",
        queue.QueueUrl,
        attrs
      )

      val entries = JsArray(
        receiveMessage(queue.QueueUrl).Messages.map { m =>
          JsObject(
            "Id" -> JsString(s"${m.MessageId}"),
            "ReceiptHandle" -> JsString(s"${m.ReceiptHandle}"),
            "VisibilityTimeout" -> JsNumber(500)
          )
        }.toVector
      ).compactPrint

      // then
      s"""${version.executable} sqs change-message-visibility-batch --entries='$entries' --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=${queue.QueueUrl}""" !!
    }

    test(s"should set and get queue attributes with ${version.name}", Only213) {
      val queue = createQueue("test")

      // when
      s"${version.executable} sqs set-queue-attributes --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url ${queue.QueueUrl} --attributes='VisibilityTimeout=99'" !!

      val result =
        s"${version.executable} sqs get-queue-attributes --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url ${queue.QueueUrl} --attribute-names='VisibilityTimeout'" !!

      result.parseJson.convertTo[GetQueueAttributesResponse].Attributes.get("VisibilityTimeout") shouldBe Some("99")
    }

    test(s"should get source queues of a dlq with ${version.name}", Only213) {
      // given
      val dlq = createQueue("testDlq")
      val redrivePolicy =
        RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.toString.replaceAll("\"", "\\\\\"")
      val main = createQueue("main", Some(s"""{"RedrivePolicy":"$redrivePolicy"}"""))

      // when
      val response =
        s"""${version.executable} sqs list-dead-letter-source-queues --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url ${dlq.QueueUrl}""" !!

      // then
      val result = response.parseJson.convertTo[ListDeadLetterSourceQueuesResponse]
      result.queueUrls should contain only (main.QueueUrl)
    }
  }
}

case class AWSCli(executable: String, name: String)
