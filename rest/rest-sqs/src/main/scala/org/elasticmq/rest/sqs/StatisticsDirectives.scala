package org.elasticmq.rest.sqs

import java.util.concurrent.Executors

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.elasticmq.QueueStatistics
import org.elasticmq.metrics.QueueMetricsOps
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.util.NowProvider
import spray.json._

import scala.concurrent.{Await, ExecutionContext}

final case class QueueResponse(
                                name: String,
                                statistics: QueueStatisticsResponse
                              )

final case class QueueStatisticsResponse(
                                          approximateNumberOfVisibleMessages: Long,
                                          approximateNumberOfInvisibleMessages: Long,
                                          approximateNumberOfMessagesDelayed: Long
                                        )

//type QueueAttributesRest Map[String, String]

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queueStatisticsFormat = jsonFormat3(QueueStatisticsResponse)
  implicit val queueFormat = jsonFormat2(QueueResponse)
}

trait StatisticsDirectives extends JsonSupport {
  this: ElasticMQDirectives =>

  lazy val np = new NowProvider
  lazy val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
  implicit val duration = timeout.duration

  def statistics = {
    pathPrefix("statistics" / "queues") {
      concat(
        pathEndOrSingleSlash {
          complete {
            gatherAllQueuesWithStats
          }
        },
        path(Segment) { queueName =>
          complete {
            gatherSpecificQueueWithStats(queueName)
          }
        }
      )
    }
  }

  def gatherAllQueuesWithStats = {

    Await.result(
      QueueMetricsOps.getQueuesStatistics(queueManagerActor, np), duration)
      .map { case (name, stats) => mapToRest(name, stats) }
      .toList
  }

  def gatherSpecificQueueWithStats(queueName: String) = {

    val eventualResponse = QueueMetricsOps.getQueueStatistics(queueName, queueManagerActor, np)
      .map(stats => mapToRest(queueName, stats))

    Await.result(eventualResponse, duration)

  }

  private def mapToRest(queueName: String, stats: QueueStatistics) = {
    QueueResponse(queueName, QueueStatisticsResponse(
      approximateNumberOfInvisibleMessages = stats.approximateNumberOfInvisibleMessages,
      approximateNumberOfMessagesDelayed = stats.approximateNumberOfMessagesDelayed,
      approximateNumberOfVisibleMessages = stats.approximateNumberOfVisibleMessages
    ))
  }
}
