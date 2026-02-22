package org.elasticmq.rest.sqs

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.QueueEvent
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.{SqlQueuePersistenceActor, SqlQueuePersistenceConfig}
import org.elasticmq.util.NowProvider
import org.elasticmq.{NodeAddress, StrictSQSLimits}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient => AwsSqsClient}
import org.elasticmq.rest.sqs.client.AwsSdkV2SqsClient
import java.net.URI
import scala.util.Try

trait SqlQueuePersistenceServer extends ScalaFutures {

  private val awsAccountId = "123456789012"
  private val awsRegion = "elasticmq"

  private val actorSystem: ActorSystem = ActorSystem("elasticmq-test-v2")
  private var strictServer: SQSRestServer = _

  var clientV2: AwsSqsClient = _
  var testClient: AwsSdkV2SqsClient = _
  var store: ActorRef = _

  implicit val timeout: Timeout = {
    import scala.concurrent.duration._
    Timeout(5.seconds)
  }

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  def startServerAndRun(pruneDataOnInit: Boolean)(body: => Unit): Unit = {
    startServerAndSetupClient(pruneDataOnInit)
    try {
      body
    } finally {
      stopServerAndClient()
    }
  }

  private def startServerAndSetupClient(pruneDataOnInit: Boolean): Unit = {
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
      .withPort(9323) // different port to avoid conflicts
      .withServerAddress(NodeAddress(port = 9323))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    (store ? QueueEvent.Restore(manager)).futureValue

    clientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI("http://localhost:9323"))
      .build()

    testClient = new AwsSdkV2SqsClient(clientV2)
  }

  private def stopServerAndClient(): Unit = {
    if (clientV2 != null) clientV2.close()
    if (strictServer != null) Try(strictServer.stopAndWait())
  }
}
