package org.elasticmq.storage.statemanager.command

import java.io.ObjectOutputStream
import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.storage.{UpdateMessageStatisticsCommand, SendMessageCommand, CreateQueueCommand}
import org.elasticmq.{MessageStatistics, MessageId}

class StateDumper(commandSource: MQDataSource, oos: ObjectOutputStream) {
  def dumpQueues() {
    commandSource.queuesData.foreach(dumpQueue(_))
    oos.writeObject(EndOfCommands)
  }

  def dumpQueue(queue: QueueData) {
    oos.writeObject(CreateQueueCommand(queue))
    commandSource.messagesData(queue).foreach(dumpMessage(queue, _))
    commandSource.messageStatisticsWithId(queue).foreach(msWithId => dumpMessageStatistics(queue, msWithId._1, msWithId._2))
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
