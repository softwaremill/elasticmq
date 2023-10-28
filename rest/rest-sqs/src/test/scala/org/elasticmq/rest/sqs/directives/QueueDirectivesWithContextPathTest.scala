package org.elasticmq.rest.sqs.directives

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CreateQueue
import org.elasticmq.rest.sqs.{ActorSystemModule, ContextPathModule, QueueManagerActorModule}
import org.elasticmq.util.NowProvider
import org.elasticmq.{CreateQueueData, MillisVisibilityTimeout, QueueData, StrictSQSLimits}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, OffsetDateTime}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class QueueDirectivesWithContextPathTest
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
    with AWSProtocolDirectives {

  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit lazy val actorSystem: ActorSystem = ActorSystem("lol")
  lazy val queueManagerActor: ActorRef =
    actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), StrictSQSLimits, None)))
  lazy val contextPath = "/test-context"

  "queueActorAndNameFromUrl" should "return correct queue name based on QueueName" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, OffsetDateTime.now(), OffsetDateTime.now())
      )
    )
    Await.result(future, maxDuration)
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/test-context/906175111765/lol"
      ) { (_, name) => _.complete(name) }
    }

    Get("/test-context/906175111765/lol") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }

  "queueActorAndNameFromUrl" should "return correct queue name based on QueueUrl" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, OffsetDateTime.now(), OffsetDateTime.now())
      )
    )
    Await.result(future, maxDuration)
    val route = {
      queueActorAndNameFromUrl(
        "https://eu-central-1.queue.amazonaws.com/test-context/906175111765/lol"
      ) { (_, name) => _.complete(name) }
    }

    Get("/test-context") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }
}
