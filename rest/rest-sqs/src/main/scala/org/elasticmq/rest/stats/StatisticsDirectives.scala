package org.elasticmq.rest.stats

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.model.StatusCodes.NotFound
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import org.apache.pekko.http.scaladsl.server.Route
import org.elasticmq.metrics.QueueMetricsOps
import org.elasticmq.rest.sqs.QueueAttributesOps
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.util.NowProvider
import org.elasticmq.{QueueData, QueueStatistics}

import java.io.File
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

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

trait StatisticsDirectives extends StatisticsJsonFormat {
  this: ElasticMQDirectives with QueueAttributesOps =>

  sealed trait UILocation {
    def isResourceBased: Boolean = this match {
      case ResourceBased => true
      case FileBased     => false
    }
  }
  case object ResourceBased extends UILocation
  case object FileBased extends UILocation

  lazy val uiLocation: UILocation =
    if (new File("index.html").exists) FileBased
    else if (Try(Source.fromResource("index.html")).toOption.isDefined) ResourceBased
    else throw new RuntimeException("Could not find UI files")

  implicit val duration = timeout.duration

  def statistics(implicit np: NowProvider) = {
    pathPrefix("statistics" / "queues") {
      concat(
        pathEndOrSingleSlash {
          respondWithHeader(`Access-Control-Allow-Origin`.*) {
            complete(gatherAllQueuesWithStats)
          }
        },
        path(Segment) { queueName =>
          respondWithHeader(`Access-Control-Allow-Origin`.*) {
            gatherSpecificQueueWithAttributesRoute(queueName)
          }
        }
      )
    } ~
      serveUI
  }

  def gatherAllQueuesWithStats(implicit np: NowProvider): Future[Iterable[QueuesResponse]] = {

    QueueMetricsOps
      .getQueuesStatistics(queueManagerActor, np)
      .map { x => x.map { case (name, stats) => mapToRestQueuesResponse(name, stats) } }
  }

  def gatherSpecificQueueWithAttributesRoute(queueName: String): Route = {
    queueActorAndDataFromQueueName(queueName) { (queueActor, queueData) =>
      onComplete(getQueryResponseWithAttributesFuture(queueName, queueActor, queueData)) {
        case Success(value) => complete(value)
        case Failure(ex) =>
          logger.error(s"Error while loading statistics for queue ${queueName}", ex)
          complete(NotFound, s"Can't load data for queue ${queueName}")
      }
    }
  }

  def serveUI = {
    get {
      entity(as[HttpRequest]) { requestData =>
        requestData.uri.path.toString match {
          case "/" =>
            if (uiLocation.isResourceBased) getFromResource("index.html") else getFromFile("index.html")
          case "" =>
            if (uiLocation.isResourceBased) getFromResource("index.html") else getFromFile("index.html")
          case path =>
            // We have to drop leading slash in order to successfully find files
            if (uiLocation.isResourceBased) getFromResource(path.drop(1)) else getFromFile(path.drop(1))
        }
      }
    }
  }

  private def getQueryResponseWithAttributesFuture(
      name: String,
      queueActor: ActorRef,
      queueData: QueueData
  ): Future[QueueResponse] = {
    getAllQueueAttributes(queueActor, queueData)
      .map(list => QueueResponse(name = name, list.toMap))
  }

  private def mapToRestQueuesResponse(queueName: String, statistics: QueueStatistics): QueuesResponse = {
    QueuesResponse(
      queueName,
      QueueStatisticsResponse(
        approximateNumberOfInvisibleMessages = statistics.approximateNumberOfInvisibleMessages,
        approximateNumberOfMessagesDelayed = statistics.approximateNumberOfMessagesDelayed,
        approximateNumberOfVisibleMessages = statistics.approximateNumberOfVisibleMessages
      )
    )
  }
}
