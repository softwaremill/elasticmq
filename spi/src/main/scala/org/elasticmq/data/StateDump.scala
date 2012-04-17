package org.elasticmq.data

import org.elasticmq.{MessageStatistics, MessageId}
import org.elasticmq.storage.{IdempotentMutativeCommand, UpdateMessageStatisticsCommand, SendMessageCommand, CreateQueueCommand}

class StateDump(dataSource: DataSource) {
  import StateDump._

  def createStream(): CommandStream = {
    dataSource.queuesData.toStream.flatMap(dumpQueue(_))
  }

  private def dumpQueue(queue: QueueData): CommandStream = {
    val messageDataStream = dataSource.messagesData(queue).toStream.map(dumpMessage(queue, _))
    val messageStatsStream = dataSource.messageStatisticsWithId(queue).toStream
      .flatMap(msWithId => dumpMessageStatistics(queue, msWithId._1, msWithId._2))

    Stream.cons(
      CreateQueueCommand(queue),
      messageDataStream) ++
      messageStatsStream
  }

  private def dumpMessage(queue: QueueData, message: MessageData): SendMessageCommand = {
    SendMessageCommand(queue.name, message)
  }

  private def dumpMessageStatistics(queue: QueueData, messageId: MessageId, messageStatistics: MessageStatistics): Option[UpdateMessageStatisticsCommand] = {
    // If the receive count is 0, the same object will be created when executing the send command
    if (messageStatistics.approximateReceiveCount != 0) {
      Some(UpdateMessageStatisticsCommand(queue.name, messageId, messageStatistics))
    } else {
      None
    }
  }
}

object StateDump {
  type CommandStream = Stream[IdempotentMutativeCommand[_]]
}