package org.elasticmq.rest.sqs

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.{CreateQueueRequest, ReceiveMessageRequest, SendMessageRequest}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.{OperationStatus, OperationSuccessful, Restore}
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.{SqlQueuePersistenceActor, SqlQueuePersistenceConfig}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{ElasticMQError, NodeAddress, StrictSQSLimits}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.Await
import scala.util.Try

class MessagePersistenceTest extends AnyFunSuite with BeforeAndAfter with Matchers with Logging {

  private val awsAccountId = "123456789012"
  private val awsRegion = "elasticmq"

  private var strictServer: SQSRestServer = _
  private var client: AmazonSQS = _

  private val actorSystem: ActorSystem = ActorSystem("elasticmq-test")

  test("should persist and read the messages after restart") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 4"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 5"))
    }

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(5)).getMessages

      val bodies = messages.asScala.map(_.getBody).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3", "Message 4", "Message 5"))
    }
  }

  private def startServerAndSetupClient(pruneDataOnInit: Boolean): Unit = {
    implicit val timeout: Timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    val persistenceConfigPrune = SqlQueuePersistenceConfig(
      enabled = true,
      driverClass = "org.sqlite.JDBC",
      uri = "jdbc:sqlite:./elastimq.db",
      pruneDataOnInit = pruneDataOnInit)

    val store = actorSystem.actorOf(Props(new SqlQueuePersistenceActor(persistenceConfigPrune, List.empty)))
    val manager = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), StrictSQSLimits, Some(store))))

    strictServer = SQSRestServerBuilder
      .withActorSystem(actorSystem)
      .withQueueManagerActor(manager)
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    val restore: Either[List[ElasticMQError], OperationStatus] = Await.result(store ? Restore(manager), timeout.duration)
    restore shouldBe Right(OperationSuccessful)

    client = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9321", "us-east-1"))
      .build()
  }

  private def stopServerAndClient(): Unit = {
    client.shutdown()
    Try(strictServer.stopAndWait())
  }

  private def startServerAndRun(pruneDataOnInit: Boolean)(body: => Unit): Unit = {
    startServerAndSetupClient(pruneDataOnInit)
    body
    stopServerAndClient()
  }
}
