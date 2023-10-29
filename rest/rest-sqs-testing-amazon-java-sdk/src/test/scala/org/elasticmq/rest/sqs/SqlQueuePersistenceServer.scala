package org.elasticmq.rest.sqs

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.util.Timeout
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.QueueEvent
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.{SqlQueuePersistenceActor, SqlQueuePersistenceConfig}
import org.elasticmq.util.NowProvider
import org.elasticmq.{NodeAddress, StrictSQSLimits}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.util.Try

trait SqlQueuePersistenceServer extends ScalaFutures {

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

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  def startServerAndRun(pruneDataOnInit: Boolean)(body: => Unit): Unit = {
    startServerAndSetupClient(pruneDataOnInit)
    body
    stopServerAndClient()
  }

  private def startServerAndSetupClient(pruneDataOnInit: Boolean): Unit = {
    val persistenceConfig = SqlQueuePersistenceConfig(
      enabled = true,
      driverClass = "org.h2.Driver",
      uri = "jdbc:h2:./elasticmq-h2",
      pruneDataOnInit = pruneDataOnInit
    )

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

    (store ? QueueEvent.Restore(manager)).futureValue

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
