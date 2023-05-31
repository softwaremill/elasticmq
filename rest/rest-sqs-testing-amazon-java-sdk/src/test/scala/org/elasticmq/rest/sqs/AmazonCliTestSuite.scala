package org.elasticmq.rest.sqs

import org.elasticmq.MessageAttribute
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.sys.process._
import scala.language.postfixOps
import spray.json._

class AmazonCliTestSuite
    extends SqsClientServerCommunication
    with Matchers
    with TableDrivenPropertyChecks
    with OptionValues {

  val cliVersions = Table(
    "cli version",
    AWSCli(Option(System.getenv("AWS_CLI_V1_EXECUTABLE")).getOrElse("aws1")),
    AWSCli(Option(System.getenv("AWS_CLI_V2_EXECUTABLE")).getOrElse("aws"))
  )

  def createQueue(name: String)(implicit cli: AWSCli): CreateQueueResponse = {
    val result =
      s"""${cli.executable} sqs create-queue --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-name=$name""" !!

    result.parseJson.convertTo[CreateQueueResponse]
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

    result.parseJson.convertTo[ListQueuesResponse]
  }

  def sendMessage(messageBody: String, queueUrl: String)(implicit cli: AWSCli): SendMessageResponse = {
    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  def sendMessageWithAttributes(messageBody: String, queueUrl: String, messageAttributes: String)(implicit
      cli: AWSCli
  ): SendMessageResponse = {
    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody --message-attributes='$messageAttributes'""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  def sendMessageWithAttributesAndSystemAttributes(
      messageBody: String,
      queueUrl: String,
      messageAttributes: String,
      systemAttributes: String
  )(implicit cli: AWSCli): SendMessageResponse = {
    val result =
      s"""${cli.executable} sqs send-message --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-url=$queueUrl --message-body=$messageBody --message-attributes='$messageAttributes' --message-system-attributes='$systemAttributes'""" !!

    result.parseJson.convertTo[SendMessageResponse]
  }

  test("should create a queue and get queue url") {

    forAll(cliVersions) { implicit version =>
      val create = createQueue("test-queue")

      val get = getQueueUrl("test-queue")

      get.QueueUrl shouldBe create.QueueUrl
    }
  }

  test("should list created queues") {

    forAll(cliVersions) { implicit version =>
      createQueue("test-queue1")
      createQueue("test-queue2")

      val get = listQueues()

      get.QueueUrls should contain allOf (s"$ServiceEndpoint/$awsAccountId/test-queue2", s"$ServiceEndpoint/$awsAccountId/test-queue1")
    }

  }

  test("should list queues with the specified prefix") {
    forAll(cliVersions) { implicit version =>
      // Given
      createQueue("aaa-test-queue1")
      createQueue("aaa-test-queue2")
      createQueue("bbb-test-queue2")

      // When
      val get = listQueues(prefix = Some("aaa"))

      get.QueueUrls.length shouldBe 2
      get.QueueUrls should contain allOf (s"$ServiceEndpoint/$awsAccountId/aaa-test-queue2", s"$ServiceEndpoint/$awsAccountId/aaa-test-queue1")
    }
  }

  test("should send a single message to queue") {
    forAll(cliVersions) { implicit version =>
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
  }

  test("should send a single message with Attributes to queue") {
    forAll(cliVersions) { implicit version =>
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
  }

  test("should send a single message with Attributes and System Attributes to queue") {
    forAll(cliVersions) { implicit version =>
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
      // message.MD5OfMessageSystemAttributes.isEmpty shouldBe false TODO it's not calculated atm
      message.SequenceNumber shouldBe empty
    }
  }

}

case class AWSCli(executable: String)
