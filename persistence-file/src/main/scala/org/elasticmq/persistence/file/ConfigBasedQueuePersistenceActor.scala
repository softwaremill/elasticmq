package org.elasticmq.persistence.file

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.elasticmq.actor.queue._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CreateQueue
import org.elasticmq.persistence.{CreateQueueMetadata}
import org.elasticmq.util.Logging
import org.elasticmq.{ElasticMQError, QueueData}

import scala.collection.mutable
import scala.concurrent.Await

class ConfigBasedQueuePersistenceActor(storagePath: String, baseQueues: List[CreateQueueMetadata])
    extends Actor
    with Logging {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

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

    case QueueEvent.MessageAdded | QueueEvent.MessageUpdated | QueueEvent.MessageRemoved =>
      sender() ! OperationUnsupported

    case QueueEvent.Restore(queueManagerActor: ActorRef) =>
      sender() ! createQueues(queueManagerActor)
  }

  private def createQueues(queueManagerActor: ActorRef): Either[List[ElasticMQError], Unit] = {
    implicit val timeout: Timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    val queuesToCreate = CreateQueueMetadata.mergePersistedAndBaseQueues(
      QueueConfigUtil.readPersistedQueuesFromPath(storagePath),
      baseQueues
    )
    val errors = queuesToCreate
      .flatMap((cq: CreateQueueMetadata) =>
        Await.result(queueManagerActor ? CreateQueue(cq.toQueueData), timeout.duration).swap.toOption
      )

    if (errors.nonEmpty) Left(errors) else Right(())
  }
}
