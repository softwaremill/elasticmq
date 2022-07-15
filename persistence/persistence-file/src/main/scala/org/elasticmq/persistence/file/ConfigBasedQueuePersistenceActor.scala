package org.elasticmq.persistence.file

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.elasticmq.actor.queue._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CreateQueue
import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.util.Logging
import org.elasticmq.{ElasticMQError, QueueData}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ConfigBasedQueuePersistenceActor(storagePath: String, baseQueues: List[CreateQueueMetadata])
    extends Actor
    with Logging {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val ec: ExecutionContext = context.dispatcher

  def receive: Receive = {
    case QueueEvent.QueueCreated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case QueueEvent.QueueDeleted(queueName) =>
      queues.remove(queueName)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case QueueEvent.QueueMetadataUpdated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case _: QueueEvent.MessageAdded | _: QueueEvent.MessageUpdated | _: QueueEvent.MessageRemoved =>
      sender() ! OperationUnsupported

    case QueueEvent.Restore(queueManagerActor: ActorRef) =>
      val recip = sender()
      createQueues(queueManagerActor).onComplete {
        case Success(result)    => recip ! result
        case Failure(exception) => logger.error("Failed to restore stored queues", exception)
      }
  }

  private def createQueues(queueManagerActor: ActorRef): Future[Either[List[ElasticMQError], Unit]] = {
    val queuesToCreate = CreateQueueMetadata.mergePersistedAndBaseQueues(
      QueueConfigUtil.readPersistedQueuesFromPath(storagePath),
      baseQueues
    )

    val createQueuesFutures = queuesToCreate.map { createQueue =>
      (queueManagerActor ? CreateQueue(createQueue.toQueueData)).map(_.swap.toOption)
    }

    Future.sequence(createQueuesFutures).map { maybeErrors =>
      val errors = maybeErrors.flatten
      if (errors.nonEmpty) Left(errors) else Right(())
    }
  }
}
