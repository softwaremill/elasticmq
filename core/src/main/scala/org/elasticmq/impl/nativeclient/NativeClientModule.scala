package org.elasticmq.impl.nativeclient

import org.elasticmq._
import org.elasticmq.data.QueueData
import org.elasticmq.impl.NowModule
import org.elasticmq.storage.QueueStorageModule
import com.weiglewilczek.slf4s.Logging

trait NativeClientModule {
  this: QueueStorageModule with NowModule with NativeQueueModule =>

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

      queueStorage.persistQueue(queueData)

      logger.debug("Created queue: %s".format(queueBuilder.name))

      new NativeQueue(queueData)
    }

    def lookupQueue(name: String) = queueStorage.lookupQueue(name).map(new NativeQueue(_))

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

    def listQueues = queueStorage.listQueues.map(new NativeQueue(_))

    def queueOperations(name: String) = new NativeQueue(name)
  }

  val nativeClient: Client = new NativeClient
}