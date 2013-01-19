package org.elasticmq.impl.nativeclient

import org.elasticmq._
import org.elasticmq.data.QueueData
import org.elasticmq.impl.NowModule
import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.storage.{ListQueuesCommand, CreateQueueCommand, LookupQueueCommand, StorageModule}

trait NativeClientModule {
  this: StorageModule with NowModule with NativeQueueModule =>

  class NativeClient extends Client with Logging {
    def createQueue(name: String): Queue = createQueue(QueueBuilder(name))

    def createQueue(queueBuilder: QueueBuilder) = {
      val queueData = QueueData(
        queueBuilder.name,
        queueBuilder.defaultVisibilityTimeout,
        queueBuilder.delay,
        nowAsDateTime,
        nowAsDateTime
      )

      storageCommandExecutor.execute(new CreateQueueCommand(queueData))

      logger.debug("Created queue: %s".format(queueBuilder.name))

      new NativeQueue(queueData)
    }

    def lookupQueue(name: String) = storageCommandExecutor.execute(LookupQueueCommand(name)).map(new NativeQueue(_))

    def lookupOrCreateQueue(name: String): Queue = lookupOrCreateQueue(QueueBuilder(name))

    def lookupOrCreateQueue(queueBuilder: QueueBuilder) = lookupQueue(queueBuilder.name) match {
      case None => try {
        createQueue(queueBuilder)
      } catch {
        // Somebody created the same queue meanwhile. Trying again.
        case _: QueueDoesNotExistException => lookupOrCreateQueue(queueBuilder)
      }
      case Some(queue) => queue
    }

    def listQueues = storageCommandExecutor.execute(new ListQueuesCommand()).map(new NativeQueue(_))

    def queueOperations(name: String) = new NativeQueue(name)
  }

  val nativeClient: Client = new NativeClient
}