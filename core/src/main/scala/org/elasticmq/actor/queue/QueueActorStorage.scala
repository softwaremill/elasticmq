package org.elasticmq.actor.queue

import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.apache.pekko.util.Timeout
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

    def mapResult[U](f: T => U): ResultWithEvents[U] = {
      ResultWithEvents(result.map(f), events)
    }

    def send[U](recipient: Option[ActorRef] = None): ReplyAction[U] = {
      val notificationF =
        if (events == Nil || queueEventListener.isEmpty) {
          Future.successful(OperationUnsupported)
        } else {
          events.foldLeft(Future.successful(List.empty[OperationStatus])) { (prevFuture, nextEvent) =>
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
            case Some(r) =>
              logger.debug(s"Sending message $r from ${context.self} to $actualSender")
              actualSender ! r
            case None =>
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
    def valueWithEvents[T](result: T, events: List[QueueEventWithOperationStatus]): ResultWithEvents[T] =
      ResultWithEvents(Some(result), events)

    def onlyValue[T](result: T): ResultWithEvents[T] =
      ResultWithEvents(Some(result), List.empty)

    def onlyEvents[T](events: List[QueueEventWithOperationStatus]): ResultWithEvents[T] =
      ResultWithEvents(None, events)

    def empty[T]: ResultWithEvents[T] = ResultWithEvents(None, List.empty)
  }
}
