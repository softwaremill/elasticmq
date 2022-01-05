package org.elasticmq.rest.sqs

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer, MultipleContainers}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{BindMode, Network}
import org.testcontainers.images.builder.ImageFromDockerfile
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser

class ITMessageTransferringToDlq extends AnyFlatSpec with ForAllTestContainer with Matchers {

  val network = Network.newNetwork()

  val elasticMQContainer = GenericContainer(
    "softwaremill/elasticmq-native",
    exposedPorts = Seq(9324, 9325),
    waitStrategy = Wait.forHttp("/"),
    classpathResourceMapping = Seq(("custom.conf", "opt/elasticmq.conf", BindMode.READ_ONLY))
  ).configure(c => c.withNetwork(network))

  val awsCLIContainer: GenericContainer =
    GenericContainer(new ImageFromDockerfile().withDockerfileFromBuilder(builder => {
      builder.from("amazon/aws-cli").entryPoint("tail", "-f", "/dev/null").build()
    })).configure(c => c.withNetwork(network))

  override val container: MultipleContainers = MultipleContainers(elasticMQContainer, awsCLIContainer)

  case class Message(
      MessageId: String,
      Body: Option[String],
      MD5OfBody: Option[String],
      MD5OfMessageBody: Option[String],
      ReceiptHandle: Option[String]
  )
  case class Messages(Messages: Array[Message])

  implicit val messageFormat = jsonFormat5(Message)
  implicit val messagesFormat = jsonFormat1(Messages)

  it should "elasticmq ran from docker image should send message to dlq after exceeding maxReceiveCount" in {

    //    given
    val elasticMQnetworkAlias = elasticMQContainer.networkAliases(0)

    awsCLIContainer.execInContainer("aws", "configure", "set", "aws_access_key_id", "x")
    awsCLIContainer.execInContainer("aws", "configure", "set", "aws_secret_access_key", "x")
    awsCLIContainer.execInContainer("aws", "configure", "set", "region", "elasticmq")

    val sentMessage = awsCLIContainer
      .execInContainer(
        "aws",
        "--endpoint-url",
        s"http://${elasticMQnetworkAlias}:9324",
        "sqs",
        "send-message",
        "--queue-url",
        s"http://${elasticMQnetworkAlias}:9324/queue/main",
        "--message-body",
        "\"Hello, queue\""
      )
      .getStdout

    //    when
    val firstReceive = awsCLIContainer
      .execInContainer(
        "aws",
        "--endpoint-url",
        s"http://${elasticMQnetworkAlias}:9324",
        "sqs",
        "receive-message",
        "--queue-url",
        s"http://${elasticMQnetworkAlias}:9324/queue/main",
        "--wait-time-seconds",
        "10"
      )
      .getStdout

    val secondReceive = awsCLIContainer
      .execInContainer(
        "aws",
        "--endpoint-url",
        s"http://${elasticMQnetworkAlias}:9324",
        "sqs",
        "receive-message",
        "--queue-url",
        s"http://${elasticMQnetworkAlias}:9324/queue/main",
        "--wait-time-seconds",
        "10"
      )
      .getStdout

    val messageFromDlq = awsCLIContainer
      .execInContainer(
        "aws",
        "--endpoint-url",
        s"http://${elasticMQnetworkAlias}:9324",
        "sqs",
        "receive-message",
        "--queue-url",
        s"http://${elasticMQnetworkAlias}:9324/queue/retry"
      )
      .getStdout

    //    then
    val createdMessageId = JsonParser(sentMessage).asJsObject.convertTo[Message].MessageId
    val receivedMessageId = JsonParser(firstReceive).asJsObject.convertTo[Messages].Messages(0).MessageId
    val dlqMessageId = JsonParser(messageFromDlq).asJsObject.convertTo[Messages].Messages(0).MessageId

    createdMessageId shouldBe receivedMessageId
    receivedMessageId shouldBe dlqMessageId
    secondReceive shouldBe empty
  }
}
