package org.elasticmq.persistence.file

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.elasticmq.actor.queue._
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.{CreateQueueMetadata, QueueConfigUtil}
import org.elasticmq.util.Logging
import org.elasticmq.{ElasticMQError, QueueData}
import org.joda.time.DateTime

import scala.collection.mutable
import scala.concurrent.Await

class ConfigBasedQueuePersistenceActor(storagePath: String, baseQueues: List[CreateQueueMetadata]) extends Actor with Logging {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  def receive: Receive = {
    case QueueCreated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case QueueDeleted(queueName) =>
      queues.remove(queueName)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case QueueMetadataUpdated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)

    case AddMessage(_, _) =>
      sender() ! OperationUnsupported

    case UpdateMessage(_, _) =>
      sender() ! OperationUnsupported

    case RemoveMessage(_, _) =>
      sender() ! OperationUnsupported

    case Restore(queueManagerActor: ActorRef) =>
      sender() ! createQueues(queueManagerActor)
  }

  private def createQueues(queueManagerActor: ActorRef): Either[List[ElasticMQError], Unit] = {
    implicit val timeout: Timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    val queuesToCreate = QueueConfigUtil.getQueuesToCreate(QueueConfigUtil.readPersistedQueuesFromPath(storagePath), baseQueues)
    val errors = queuesToCreate
      .flatMap((cq: CreateQueueMetadata) =>
        Await.result(queueManagerActor ? org.elasticmq.msg.CreateQueue(cq.toQueueData(new DateTime())), timeout.duration)
          .swap
          .toOption
      )

    if (errors.nonEmpty) Left(errors) else Right()
  }
}
