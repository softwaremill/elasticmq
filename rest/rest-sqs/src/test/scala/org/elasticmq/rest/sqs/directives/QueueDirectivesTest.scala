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
import org.joda.time.{DateTime, Duration}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class QueueDirectivesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with QueueDirectives
    with ContextPathModule
    with AnyParamDirectives
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
  lazy val contextPath = ""

  "queueActorAndNameFromRequest" should "return correct queue name based on QueueName" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, DateTime.now(), DateTime.now())
      )
    )
    Await.result(future, maxDuration)
    val route = {
      queueActorAndNameFromRequest(
        Map("QueueName" -> "lol", "QueueUrl" -> "https://eu-central-1.queue.amazonaws.com/906175111765/lol")
      ) { (_, name) => _.complete(name) }
    }

    Get("/906175111765/lol") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }

  "queueActorAndNameFromRequest" should "return correct queue name based on QueueUrl" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, DateTime.now(), DateTime.now())
      )
    )
    Await.result(future, maxDuration)
    val route = {
      queueActorAndNameFromRequest(
        Map("QueueUrl" -> "https://eu-central-1.queue.amazonaws.com/906175111765/lol")
      ) { (_, name) => _.complete(name) }
    }

    Get("/") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }

  "queueActorAndNameFromRequest" should "return error when invalid QueueUrl" in {
    val future = queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, DateTime.now(), DateTime.now())
      )
    )
    Await.result(future, maxDuration)
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          queueActorAndNameFromRequest(
            Map("QueueUrl" -> "https://eu-central-1.queue.amazonaws.com")
          ) { (_, name) => _.complete(name) }
        }
      }
    }

    Get("/") ~> route ~> check {
      rejections shouldNot be(empty)
    }
  }
}
