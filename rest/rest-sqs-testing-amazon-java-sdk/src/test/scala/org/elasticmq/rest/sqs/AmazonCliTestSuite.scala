package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, LoneElement, OptionValues, Tag}
import spray.json.DefaultJsonProtocol.listFormat
import spray.json._

import scala.language.postfixOps
import scala.sys.process._

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

    test(s"should send message batch with ${version.name}", Only213) {
      // given
      val queue = createQueue("test-queue")
      val firstMessageBody = "messageOne"
      val entries = s"""{"Id": "1", "MessageBody": "$firstMessageBody"}, {"Id": "2", "MessageBody": ""}"""

      // when
      val batchMessages = sendMessageBatch(queue.QueueUrl, entries)

      // then
      val failed = batchMessages.Failed.toList.flatten
      failed.size shouldBe 1
      inside(failed.head) { case failedMessage =>
        import failedMessage._
        Id shouldBe "2"
        SenderFault shouldBe true
        Code shouldBe "InvalidAttributeValue"
        Message shouldBe "The request must contain the parameter MessageBody."
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
