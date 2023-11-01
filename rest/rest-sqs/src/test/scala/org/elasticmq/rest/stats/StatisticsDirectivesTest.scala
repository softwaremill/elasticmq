package org.elasticmq.rest.stats

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply.ReplyActorRef
import org.elasticmq.msg.{CreateQueue, DeleteQueue}
import org.elasticmq.rest.sqs.directives._
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueAttributesOps, QueueManagerActorModule}
import org.elasticmq.util.MutableNowProvider
import org.elasticmq.{CreateQueueData, MillisVisibilityTimeout, QueueData, StrictSQSLimits}
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, LocalDate, ZoneOffset}
import scala.concurrent.duration.DurationInt

class StatisticsDirectivesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with QueueDirectives
    with QueueManagerActorModule
    with FutureDirectives
    with ActorSystemModule
    with ExceptionDirectives
    with RespondDirectives
    with StatisticsDirectives
    with ElasticMQDirectives
    with QueueAttributesOps
    with BeforeAndAfter
    with ScalaFutures
    with IntegrationPatience {

  def awsAccountId: String = "id"

  def awsRegion: String = "region"

  implicit lazy val timeout: Timeout = 1.minute
  implicit lazy val actorSystem: ActorSystem = ActorSystem("test-actor-system")

  implicit val nowProvider = new MutableNowProvider(
    LocalDate.of(2020, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant.toEpochMilli
  )

  lazy val queueManagerActor: ActorRef =
    actorSystem.actorOf(Props(new QueueManagerActor(nowProvider, StrictSQSLimits, None)))

  lazy val contextPath = ""

  "statisticsRequestForAllQueues" should "return all queues statistics" in {

    val route = {
      statistics
    }

    Get("/statistics/queues") ~> route ~> check {
      responseAs[List[QueuesResponse]] should contain theSameElementsAs List(
        QueuesResponse("firstQueue", QueueStatisticsResponse(0, 0, 0)),
        QueuesResponse("secondQueue", QueueStatisticsResponse(0, 0, 0))
      )
    }
  }

  "statisticsRequestForCertainQueue" should "return statistics for specified queue" in {

    val route = {
      statistics
    }

    Get("/statistics/queues/firstQueue") ~> route ~> check {
      val queueResponse = responseAs[QueueResponse]
      val expectedNowTimeInMillis = (nowProvider.nowMillis / 1000L).toString
      queueResponse.name shouldEqual "firstQueue"
      queueResponse.attributes should contain theSameElementsAs Map(
        "ApproximateNumberOfMessagesDelayed" -> "0",
        "VisibilityTimeout" -> "0",
        "ApproximateNumberOfMessagesNotVisible" -> "0",
        "LastModifiedTimestamp" -> expectedNowTimeInMillis,
        "QueueArn" -> "arn:aws:sqs:region:id:firstQueue",
        "CreatedTimestamp" -> expectedNowTimeInMillis,
        "ApproximateNumberOfMessages" -> "0",
        "ReceiveMessageWaitTimeSeconds" -> "0",
        "DelaySeconds" -> "0"
      )
    }
  }

  before {
    createQueueWithName("firstQueue")
    createQueueWithName("secondQueue")

  }

  after {
    deleteQueueWithName("firstQueue")
    deleteQueueWithName("secondQueue")
  }

  private def createQueueWithName(name: String) = {
    (queueManagerActor ? CreateQueue(
      CreateQueueData.from(
        QueueData(name, MillisVisibilityTimeout(1L), Duration.ZERO, Duration.ZERO, nowProvider.now, nowProvider.now)
      )
    )).futureValue
  }

  private def deleteQueueWithName(name: String) = {
    (queueManagerActor ? DeleteQueue(name)).futureValue

  }
}
