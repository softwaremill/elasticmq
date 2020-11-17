package org.elasticmq.rest.stats

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server.Route
import org.elasticmq.metrics.QueueMetricsOps
import org.elasticmq.rest.sqs.QueueAttributesOps
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.util.NowProvider
import org.elasticmq.{QueueData, QueueStatistics}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class QueuesResponse(
                                 name: String,
                                 statistics: QueueStatisticsResponse
                               )

final case class QueueResponse(
                                name: String,
                                attributes: Attributes
                              )

final case class QueueStatisticsResponse(
                                          approximateNumberOfVisibleMessages: Long,
                                          approximateNumberOfInvisibleMessages: Long,
                                          approximateNumberOfMessagesDelayed: Long
                                        )


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queueStatisticsFormat = jsonFormat3(QueueStatisticsResponse)
  implicit val queuesFormat = jsonFormat2(QueuesResponse)
  implicit val queueFormat = jsonFormat2(QueueResponse)
}

trait StatisticsDirectives extends JsonSupport {
  this: ElasticMQDirectives with QueueAttributesOps  =>

  lazy val ec: ExecutionContext = actorSystem.dispatcher
  implicit val duration = timeout.duration

  def statistics(implicit np: NowProvider) = {
    pathPrefix("statistics" / "queues") {
      concat(
        pathEndOrSingleSlash {
          complete {
            gatherAllQueuesWithStats
          }
        },
        path(Segment) { queueName =>
          gatherSpecificQueueWithAttributesRoute(queueName)
        }
      )
    }
  }

  def gatherAllQueuesWithStats(implicit np: NowProvider): Future[Iterable[QueuesResponse]] = {

    QueueMetricsOps.getQueuesStatistics(queueManagerActor, np)
      .map { x => x.map { case (name, stats) => mapToRestQueuesResponse(name, stats) } }
  }

  def gatherSpecificQueueWithAttributesRoute(queueName: String): Route = {
    val map = Map("QueueName" -> queueName)
    queueActorAndDataFromRequest(map) { (queueActor, queueData) =>
      onComplete(asQueryResponse(queueName, map, queueActor, queueData)) {
        case Success(value) => complete(value)
        case Failure(ex) =>
          logger.error(s"Error while loading statistics for queue ${queueName}", ex)
          complete(NotFound, s"Can't load data for queue ${queueName}")
      }
    }
  }

  private def asQueryResponse(name: String, map: Map[String, String], queueActor: ActorRef, queueData: QueueData): Future[QueueResponse] = {
    getAllQueueAttributes(map, queueActor, queueData)
      .map(list => QueueResponse(name = name, list.toMap))
  }

  private def mapToRestQueuesResponse(queueName: String, statistics: QueueStatistics): QueuesResponse = {
    QueuesResponse(queueName,
      QueueStatisticsResponse(
        approximateNumberOfInvisibleMessages = statistics.approximateNumberOfInvisibleMessages,
        approximateNumberOfMessagesDelayed = statistics.approximateNumberOfMessagesDelayed,
        approximateNumberOfVisibleMessages = statistics.approximateNumberOfVisibleMessages
      )
    )
  }
}
