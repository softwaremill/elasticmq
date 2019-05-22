package org.elasticmq.rest.sqs.directives
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CreateQueue
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule}
import org.elasticmq.util.NowProvider
import org.elasticmq.{MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

class QueueDirectivesTest
    extends FlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with QueueDirectives
    with AnyParamDirectives
    with QueueManagerActorModule
    with FutureDirectives
    with ActorSystemModule
    with ExceptionDirectives
    with RespondDirectives {

  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit lazy val actorSystem: ActorSystem = ActorSystem("lol")
  lazy val queueManagerActor: ActorRef = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider())))

  "queueActorAndNameFromRequest" should "return correct queue name" in {
    val future = queueManagerActor ? CreateQueue(
      QueueData("lol", MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, DateTime.now(), DateTime.now())
    )
    Await.result(future, maxDuration)
    val route = {
      queueActorAndNameFromRequest(
        Map("QueueName" -> "lol", "QueueUrl" -> "https://eu-central-1.queue.amazonaws.com/906175111765/lol")) {
        (actor, name) =>
          _.complete(name)
      }
    }

    Get("/906175111765/lol") ~> route ~> check {
      responseAs[String] shouldEqual "lol"
    }
  }
}
