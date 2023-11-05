package org.elasticmq.actor

import org.apache.pekko.actor.{ActorRef, Props}
import org.apache.pekko.util.Timeout
import org.elasticmq._
import org.elasticmq.actor.queue.{QueueActor, QueueEvent}
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.reflect._
import scala.util.{Failure, Success}

class QueueManagerActor(nowProvider: NowProvider, limits: Limits, queueEventListener: Option[ActorRef])
    extends ReplyingActor
    with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev: ClassTag[QueueManagerMsg[Unit]] = classTag[M[Unit]]

  implicit lazy val ec: ExecutionContext = context.dispatcher
  implicit lazy val timeout: Timeout = 5.seconds

  case class ActorWithQueueData(actorRef: ActorRef, queueData: QueueData)
  private val queues = collection.mutable.HashMap[String, ActorWithQueueData]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]): ReplyAction[T] =
    msg match {
      case CreateQueue(request) =>
        queues.get(request.name) match {
          case Some(metadata) =>
            if (sameCreateQueueData(metadata.queueData, request)) {
              logger.debug(s"Queue already exists: $request, returning existing actor")
              Right(metadata.actorRef)
            } else {
              logger.debug(
                s"Cannot create a queue with existing name and different parameters: $request, existing: ${metadata.queueData}"
              )
              Left(new QueueAlreadyExists(request.name))
            }
          case None =>
            logger.info(s"Creating queue $request")
            Limits.verifyQueueName(request.name, request.isFifo, limits) match {
              case Left(error) =>
                Left(InvalidParameterValue(request.name, error))
              case Right(_) =>
                val queueData = request.toQueueData
                val actor = createQueueActor(nowProvider, queueData, queueEventListener)
                queues(request.name) = ActorWithQueueData(actor, queueData)
                queueEventListener.foreach(_ ! QueueEvent.QueueCreated(queueData))
                Right(actor)
            }
        }

      case DeleteQueue(queueName) =>
        logger.info(s"Deleting queue $queueName")
        queues.remove(queueName).foreach { case ActorWithQueueData(actorRef, _) => context.stop(actorRef) }
        queueEventListener.foreach(_ ! QueueEvent.QueueDeleted(queueName))

      case LookupQueue(queueName) =>
        val result = queues.get(queueName).map(_.actorRef)

        logger.debug(s"Looking up queue $queueName, found?: ${result.isDefined}")
        result

      case ListQueues() => queues.keySet.toSeq

      case ListDeadLetterSourceQueues(queueName) =>
        queues.collect {
          case (name, actor) if actor.queueData.deadLettersQueue.exists(_.name == queueName) => name
        }.toList

      case StartMessageMoveTask(sourceQueue, destinationQueue, maxNumberOfMessagesPerSecond) =>
        val replyTo = sender()
        val destination = destinationQueue.map(Future.successful).getOrElse {
          val queueData = sourceQueue ? GetQueueData()
          queueData.map { qd =>
            queues
              .filter { case (_, data) =>
                data.queueData.deadLettersQueue.exists(dlqd => dlqd.name == qd.name)
              }
              .head
              ._2
              .actorRef
          }
        }
        val f = destination.flatMap(destinationQueueActorRef =>
          sourceQueue ? StartMessageMoveTaskToQueue(destinationQueueActorRef, maxNumberOfMessagesPerSecond)
        )
        f.onComplete {
          case Success(value) => replyTo ! Right(value)
          case Failure(ex)    => logger.error("Failed to start message move task", ex)
        }
        DoNotReply()
    }

  protected def createQueueActor(
      nowProvider: NowProvider,
      queueData: QueueData,
      queueEventListener: Option[ActorRef]
  ): ActorRef = {
    val deadLetterQueueActor = queueData.deadLettersQueue.flatMap { qd => queues.get(qd.name).map(_.actorRef) }
    val copyMessagesToQueueActor = queueData.copyMessagesTo.flatMap { queueName =>
      queues.get(queueName).map(_.actorRef)
    }
    val moveMessagesToQueueActor = queueData.moveMessagesTo.flatMap { queueName =>
      queues.get(queueName).map(_.actorRef)
    }

    context.actorOf(
      Props(
        new QueueActor(
          nowProvider,
          queueData,
          deadLetterQueueActor,
          copyMessagesToQueueActor,
          moveMessagesToQueueActor,
          queueEventListener
        )
      )
    )
  }

  private def sameCreateQueueData(existing: QueueData, requested: CreateQueueData) = {
    !(
      (requested.defaultVisibilityTimeout.isDefined && requested.defaultVisibilityTimeout.get != existing.defaultVisibilityTimeout) ||
        (requested.delay.isDefined && requested.delay.get != existing.delay) ||
        (requested.receiveMessageWait.isDefined && requested.receiveMessageWait.get != existing.receiveMessageWait) ||
        (requested.deadLettersQueue.isDefined && requested.deadLettersQueue != existing.deadLettersQueue) ||
        existing.isFifo != requested.isFifo ||
        existing.hasContentBasedDeduplication != requested.hasContentBasedDeduplication ||
        (requested.copyMessagesTo.isDefined && existing.copyMessagesTo != requested.copyMessagesTo) ||
        (requested.moveMessagesTo.isDefined && existing.moveMessagesTo != requested.moveMessagesTo) ||
        (requested.tags.nonEmpty && existing.tags != requested.tags)
    )
  }
}
