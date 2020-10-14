package org.elasticmq.actor

import akka.actor.{ActorRef, Props}
import org.elasticmq.actor.queue.QueueActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, DeleteQueue, _}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq._

import scala.reflect._

class QueueManagerActor(nowProvider: NowProvider, sqsLimit: SQSLimits) extends ReplyingActor with Logging {
  type M[X] = QueueManagerMsg[X]
  val ev: ClassTag[QueueManagerMsg[Unit]] = classTag[M[Unit]]
  val MaximumQueueNameLength = 80

  private val queues = collection.mutable.HashMap[String, ActorRef]()

  def receiveAndReply[T](msg: QueueManagerMsg[T]): ReplyAction[T] =
    msg match {
      case CreateQueue(queueData) =>
        if (queues.contains(queueData.name)) {
          logger.debug(s"Cannot create queue, as it already exists: $queueData")
          Left(new QueueAlreadyExists(queueData.name))
        } else {
          logger.info(s"Creating queue $queueData")
          for {
            fixedQueueName <-
              SQSLimits
                .verifyQueueName(queueData.name, queueData.isFifo, sqsLimit)
                .fold[Either[ElasticMQError, String]](
                  error => Left(QueueCreationError(queueData.name, error)),
                  name => Right(name)
                )
          } yield {
            val queueDataWithFixedName = queueData.copy(name = fixedQueueName)
            val actor = createQueueActor(nowProvider, queueDataWithFixedName)
            queues(queueDataWithFixedName.name) = actor
            actor
          }
        }

      case DeleteQueue(queueName) =>
        logger.info(s"Deleting queue $queueName")
        queues.remove(queueName).foreach(context.stop)

      case LookupQueue(queueName) =>
        val result = queues.get(queueName)

        logger.debug(s"Looking up queue $queueName, found?: ${result.isDefined}")
        result

      case ListQueues() => queues.keySet.toSeq
    }

  protected def createQueueActor(nowProvider: NowProvider, queueData: QueueData): ActorRef = {
    val deadLetterQueueActor = queueData.deadLettersQueue.flatMap { qd => queues.get(qd.name) }
    val copyMessagesToQueueActor = queueData.copyMessagesTo.flatMap { queueName => queues.get(queueName) }
    val moveMessagesToQueueActor = queueData.moveMessagesTo.flatMap { queueName => queues.get(queueName) }

    context.actorOf(
      Props(
        new QueueActor(
          nowProvider,
          queueData,
          deadLetterQueueActor,
          copyMessagesToQueueActor,
          moveMessagesToQueueActor
        )
      )
    )
  }
}
