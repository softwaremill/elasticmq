package org.elasticmq.replication.state

import org.elasticmq.{MessageStatistics, MessageId}
import org.elasticmq.data.{DataSource, MessageData, QueueData}
import org.elasticmq.storage.{StorageCommandExecutor, UpdateMessageStatisticsCommand, SendMessageCommand, CreateQueueCommand}
import java.io.{OutputStream, ObjectOutputStream}

class StateDumper(storageCommandExecutor: StorageCommandExecutor) {
  def dump(outputStream: OutputStream) {
    val oos = new ObjectOutputStream(outputStream)
    storageCommandExecutor.executeStateManagement(dataSource => {
      new DataSourceDumper(dataSource, oos).dumpQueues()
    })
  }
}

private class DataSourceDumper(dataSource: DataSource, oos: ObjectOutputStream) {
  def dumpQueues() {
    dataSource.queuesData.foreach(dumpQueue(_))
    oos.writeObject(EndOfCommands)
  }

  def dumpQueue(queue: QueueData) {
    oos.writeObject(CreateQueueCommand(queue))
    dataSource.messagesData(queue).foreach(dumpMessage(queue, _))
    dataSource.messageStatisticsWithId(queue).foreach(msWithId => dumpMessageStatistics(queue, msWithId._1, msWithId._2))
  }

  def dumpMessage(queue: QueueData, message: MessageData) {
    oos.writeObject(SendMessageCommand(queue.name, message))
  }

  def dumpMessageStatistics(queue: QueueData, messageId: MessageId, messageStatistics: MessageStatistics) {
    // If the receive count is 0, the same object will be created when executing the send command
    if (messageStatistics.approximateReceiveCount != 0) {
      oos.writeObject(UpdateMessageStatisticsCommand(queue.name, messageId, messageStatistics))
    }
  }
}
