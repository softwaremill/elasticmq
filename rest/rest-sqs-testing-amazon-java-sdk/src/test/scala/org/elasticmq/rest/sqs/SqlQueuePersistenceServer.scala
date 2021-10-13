package org.elasticmq.rest.sqs

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.Restore
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.{SqlQueuePersistenceActor, SqlQueuePersistenceConfig}
import org.elasticmq.util.NowProvider
import org.elasticmq.{NodeAddress, StrictSQSLimits}

import scala.concurrent.Await
import scala.util.Try

trait SqlQueuePersistenceServer {

  private val awsAccountId = "123456789012"
  private val awsRegion = "elasticmq"

  private val actorSystem: ActorSystem = ActorSystem("elasticmq-test")
  private var strictServer: SQSRestServer = _

  var client: AmazonSQS = _
  var store: ActorRef = _

  implicit val timeout: Timeout = {
    import scala.concurrent.duration._
    Timeout(5.seconds)
  }

  def startServerAndRun(pruneDataOnInit: Boolean)(body: => Unit): Unit = {
    startServerAndSetupClient(pruneDataOnInit)
    body
    stopServerAndClient()
  }

  private def startServerAndSetupClient(pruneDataOnInit: Boolean): Unit = {
    val persistenceConfig = SqlQueuePersistenceConfig(
      enabled = true,
      driverClass = "org.sqlite.JDBC",
      uri = "jdbc:sqlite:./elasticmq.db",
      pruneDataOnInit = pruneDataOnInit)

    store = actorSystem.actorOf(Props(new SqlQueuePersistenceActor(persistenceConfig, List.empty)))
    val manager = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), StrictSQSLimits, Some(store))))

    strictServer = SQSRestServerBuilder
      .withActorSystem(actorSystem)
      .withQueueManagerActor(manager)
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    Await.result(store ? Restore(manager), timeout.duration)

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
}
