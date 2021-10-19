package org.elasticmq.actor.queue

import akka.actor.{ActorContext, ActorRef}
import akka.util.Timeout
import org.elasticmq.actor.reply._
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{FifoDeduplicationIdsHistory, QueueData}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  var deadLettersActorRef: Option[ActorRef]
  def copyMessagesToActorRef: Option[ActorRef]
  def moveMessagesToActorRef: Option[ActorRef]
  def queueEventListener: Option[ActorRef]

  def context: ActorContext

  implicit lazy val ec: ExecutionContext = context.dispatcher
  implicit lazy val timeout: Timeout = 5.seconds

  var queueData: QueueData = initialQueueData
  var messageQueue: MessageQueue = MessageQueue(queueData.isFifo)
  var fifoMessagesHistory: FifoDeduplicationIdsHistory = FifoDeduplicationIdsHistory.newHistory()
  val receiveRequestAttemptCache = new ReceiveRequestAttemptCache

  case class ResultWithEvents[T](result: Option[T], events: List[QueueEventWithOperationStatus] = List.empty)
      extends Logging {

    def send[U](recipient: Option[ActorRef] = None): ReplyAction[U] = {
      val notificationF =
        if (events == Nil || queueEventListener.isEmpty) {
          Future.successful(OperationUnsupported)
        } else {
          events.foldLeft(Future(List.empty[OperationStatus])) { (prevFuture, nextEvent) =>
            for {
              prevResults <- prevFuture
              nextResult <- sendNotification(nextEvent)
            } yield prevResults :+ nextResult
          }
        }

      val actualSender = recipient.getOrElse(context.sender())

      notificationF.onComplete {
        case Success(_) =>
          result match {
            case Some(r) => actualSender ! r
            case None    =>
          }
        case Failure(ex) => logger.error(s"Failed to notify queue event listener. The state may be inconsistent.", ex)
      }

      DoNotReply()
    }

    private def sendNotification(event: QueueEventWithOperationStatus): Future[OperationStatus] = {
      queueEventListener
        .map(_ ? event)
        .getOrElse(Future.successful(OperationUnsupported))
    }
  }

  object ResultWithEvents {
    def some[T](result: T, events: List[QueueEventWithOperationStatus] = List.empty): ResultWithEvents[T] =
      ResultWithEvents(Some(result), events)

    def none[T](events: List[QueueEventWithOperationStatus] = List.empty): ResultWithEvents[T] =
      ResultWithEvents(None, events)
  }
}
