package org.elasticmq.rest.sqs.integration

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.QueueEvent
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.{GetAllMessages, SqlQueuePersistenceActor, SqlQueuePersistenceConfig}
import org.elasticmq.rest.sqs.integration.client.{ApproximateNumberOfMessagesAttributeName, RedrivePolicyAttributeName, VisibilityTimeoutAttributeName}
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.elasticmq.rest.sqs.integration.common.{IntegrationTestsBase, SQSRestServerWithSdkV2Client}
import org.elasticmq.util.NowProvider
import org.elasticmq.{NodeAddress, StrictSQSLimits}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import spray.json.enrichAny

import scala.concurrent.duration._

class SqlQueuePersistenceTests
    extends IntegrationTestsBase
    with SQSRestServerWithSdkV2Client
    with ScalaFutures {

  override val awsAccountId = "123456789012"
  override val awsRegion = "elasticmq"
  override val serverPort = 9323
  override val shouldStartServerAutomatically = false

  private val actorSystem: ActorSystem = ActorSystem("elasticmq-test-v2")

  var store: ActorRef = _

  implicit val timeout: Timeout = {
    import scala.concurrent.duration._
    Timeout(5.seconds)
  }

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  val testQueueName = "testQueue1"
  val deadLetterQueueName = "testDLQ1"

  def startServerAndRun(pruneDataOnInit: Boolean)(body: => Unit): Unit = {
    startServerAndSetupClient(pruneDataOnInit)
    try {
      body
    } finally {
      stopServerAndClient()
    }
  }

  def startServerAndSetupClient(pruneDataOnInit: Boolean): Unit = {
    val persistenceConfig = SqlQueuePersistenceConfig(
      enabled = true,
      driverClass = "org.h2.Driver",
      uri = "jdbc:h2:./elasticmq-h2-v2",
      pruneDataOnInit = pruneDataOnInit
    )

    store = actorSystem.actorOf(Props(new SqlQueuePersistenceActor(persistenceConfig, List.empty)))
    val manager = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), StrictSQSLimits, Some(store))))

    strictServer = SQSRestServerBuilder
      .withActorSystem(actorSystem)
      .withQueueManagerActor(manager)
      .withPort(serverPort)
      .withServerAddress(NodeAddress(port = serverPort))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    (store ? QueueEvent.Restore(manager)).futureValue

    createClients()
  }

  private def stopServerAndClient(): Unit = {
    stopClients()
    stopServers()
  }

  test("should persist the messages and after restart read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient.createQueue(testQueueName).toOption.get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val storedMessages = (store ? GetAllMessages(testQueueName)).futureValue
      storedMessages.map(_.content.value).toSet shouldBe Set("Message 1", "Message 2", "Message 3")
    }

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read and delete messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      messages.foreach(message => testClient.deleteMessage(queueUrl, message.receiptHandle))

      val storedMessages = (store ? GetAllMessages(testQueueName)).futureValue
      storedMessages.toSet shouldBe Set()
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set())
    }
  }

  test("should persist, read messages, update visibility and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      messages.foreach(message => testClient.changeMessageVisibility(queueUrl, message.receiptHandle, 2))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies1 = messages1.map(_.body).toSet
      bodies1 should be(Set())

      Thread.sleep(1000)

      val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies2 = messages2.map(_.body).toSet
      bodies2 should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should move messages to DLQ and restore") {
    startServerAndRun(pruneDataOnInit = true) {
      val dlQueueUrl = testClient
        .createQueue(deadLetterQueueName)
        .toOption
        .get

      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(
            VisibilityTimeoutAttributeName -> "1",
            RedrivePolicyAttributeName -> RedrivePolicy(deadLetterQueueName, awsAccountId, awsRegion, 1).toJson.toString
          )
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages1 should have size 3

      Thread.sleep(1000)

      val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages2 shouldBe empty

      val attributes = testClient.getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)

      attributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("0")

      val dlqAttributes = testClient.getQueueAttributes(dlQueueUrl, ApproximateNumberOfMessagesAttributeName)

      dlqAttributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("3")
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get
      val dlQueueUrl = testClient.getQueueUrl(deadLetterQueueName).toOption.get

      val attributes = testClient.getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)

      attributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("0")

      val dlqAttributes = testClient.getQueueAttributes(dlQueueUrl, ApproximateNumberOfMessagesAttributeName)

      dlqAttributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages shouldBe empty

      val dlqMessages = testClient.receiveMessage(dlQueueUrl, maxNumberOfMessages = Some(3))

      val bodies = dlqMessages.map(_.body).toSet
      bodies shouldBe Set("Message 1", "Message 2", "Message 3")
    }
  }
}
