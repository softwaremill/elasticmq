package org.elasticmq.storage.squeryl

import scala.annotation.tailrec
import java.io.{ObjectOutputStream, ObjectInputStream, OutputStream, InputStream}
import org.elasticmq.storage.interfaced.StorageStateManager
import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.storage._

trait SquerylStorageStateModule {
  this: SquerylSchemaModule =>
  
  class SquerylStorageStateManager(storageCommandExecutor: StorageCommandExecutor) extends StorageStateManager {
    def dump(outputStream: OutputStream) {
      val oos = new ObjectOutputStream(outputStream)
      new Dumper(oos).dumpQueues()
      oos.writeObject(EndOfCommands)
    }

    def restore(inputStream: InputStream) {
      val ois = new ObjectInputStream(inputStream)

      @tailrec
      def readNext() {
        val nextObject = ois.readObject()
        if (nextObject != EndOfCommands) {
          storageCommandExecutor.execute(nextObject.asInstanceOf[IdempotentMutativeCommand[_]])
          readNext()
        }
      }

      transaction {
        clearStorage()
        readNext()
      }
    }

    private def clearStorage() {
      deleteAll(messageStatistics)
      deleteAll(messages)
      deleteAll(queues)
    }
  }

  private class Dumper(oos: ObjectOutputStream) {
    def dumpQueues() {
      transaction {
        from(queues)(q => select(q)).map(_.toQueue).foreach(dumpQueue(_))
      }
    }

    def dumpQueue(queue: QueueData) {
      oos.writeObject(CreateQueueCommand(queue))
      from(messages)(m => where(m.queueName === queue.name) select(m)).map(_.toMessage).foreach(dumpMessage(queue, _))
    }
    
    def dumpMessage(queue: QueueData, message: MessageData) {
      oos.writeObject(SendMessageCommand(queue.name, message))

      messageStatistics.lookup(message.id.id).map(_.toMessageStatistics).foreach(stats => {
        // If the receive count is 0, the same object will be created when executing the send command
        if (stats.approximateReceiveCount != 0) {
          oos.writeObject(UpdateMessageStatisticsCommand(queue.name, message.id, stats))
        }
      })
    }
  }

  def storageState(storageCommandExecutor: StorageCommandExecutor) = new SquerylStorageStateManager(storageCommandExecutor)
}

private case object EndOfCommands 