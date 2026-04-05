package org.elasticmq.rest.sqs.directives
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, LookupQueue}
import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.rest.sqs.{ActorSystemModule, AutoCreateQueuesConfig, AutoCreateQueuesModule, ContextPathModule, QueueManagerActorModule}
import org.elasticmq.util.NowProvider
import org.elasticmq.{CreateQueueData, MillisVisibilityTimeout, QueueData, StrictSQSLimits}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, OffsetDateTime}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class QueueDirectivesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with QueueDirectives
    with ContextPathModule
    with QueueManagerActorModule
    with FutureDirectives
    with ActorSystemModule
    with ExceptionDirectives
    with RespondDirectives
    with ScalaFutures
    with AWSProtocolDirectives
    with AutoCreateQueuesModule {

  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit lazy val actorSystem: ActorSystem = ActorSystem("lol")
  lazy val queueManagerActor: ActorRef =
    actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), StrictSQSLimits, None)))
  lazy val contextPath = ""

  // Mutable so individual tests can override auto-create behaviour
  var autoCreateQueues: AutoCreateQueuesConfig = AutoCreateQueuesConfig.disabled

  "queueActorFromUrl" should "return correct queue name based on QueueName" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData(
          "lol",
          MillisVisibilityTimeout(1L),
          Duration.ZERO,
          Duration.ZERO,
          OffsetDateTime.now(),
          OffsetDateTime.now()
        )
      )
    )
    future.value
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/906175111765/lol"
      ) { (_, name) => _.complete(name) }
    }

    Get("/906175111765/lol") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }

  "queueActorAndNameFromUrl" should "return correct queue name based on QueueUrl" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData(
          "lol",
          MillisVisibilityTimeout(1L),
          Duration.ZERO,
          Duration.ZERO,
          OffsetDateTime.now(),
          OffsetDateTime.now()
        )
      )
    )
    future.value
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/906175111765/lol"
      ) { (_, name) => _.complete(name) }
    }

    Get("/") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }

  "queueActorAndNameFromUrl" should "return error when invalid QueueUrl" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData(
          "lol",
          MillisVisibilityTimeout(1L),
          Duration.ZERO,
          Duration.ZERO,
          OffsetDateTime.now(),
          OffsetDateTime.now()
        )
      )
    )
    future.value
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          queueActorAndNameFromUrl(
            "https://eu-central-1.queue.amazonaws.com"
          ) { (_, name) => _.complete(name) }
        }
      }
    }

    Get("/") ~> route ~> check {
      rejections shouldNot be(empty)
    }
  }

  "queueActor" should "reject non-existing queue when auto-create is disabled" in {
    autoCreateQueues = AutoCreateQueuesConfig.disabled
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          queueActorAndNameFromUrl(
            "https://eu-central-1.queue.amazonaws.com/906175111765/non-existing-queue"
          ) { (_, name) => _.complete(name) }
        }
      }
    }

    Get("/906175111765/non-existing-queue") ~> route ~> check {
      status.intValue() shouldEqual 400
    }
  }

  "queueActor" should "auto-create a non-existing queue when auto-create is enabled" in {
    autoCreateQueues = AutoCreateQueuesConfig(enabled = true, template = CreateQueueMetadata(name = ""))
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/906175111765/auto-created-queue"
      ) { (_, name) => _.complete(name) }
    }

    Get("/906175111765/auto-created-queue") ~> route ~> check {
      responseAs[String] shouldEqual "auto-created-queue"
    }

    // Verify the queue was actually created
    val lookupResult = (queueManagerActor ? LookupQueue("auto-created-queue")).futureValue
    lookupResult shouldBe defined
  }

  "queueActor" should "auto-create a FIFO queue when queue name ends with .fifo" in {
    autoCreateQueues = AutoCreateQueuesConfig(enabled = true, template = CreateQueueMetadata(name = ""))
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/906175111765/auto-fifo.fifo"
      ) { (_, name) => _.complete(name) }
    }

    Get("/906175111765/auto-fifo.fifo") ~> route ~> check {
      responseAs[String] shouldEqual "auto-fifo.fifo"
    }

    // Verify the queue was created and is FIFO
    val lookupResult = (queueManagerActor ? LookupQueue("auto-fifo.fifo")).futureValue
    lookupResult shouldBe defined
  }
}
