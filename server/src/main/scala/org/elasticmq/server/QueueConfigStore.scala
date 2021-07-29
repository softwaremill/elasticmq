package org.elasticmq.server

import org.elasticmq.QueueData
import org.elasticmq.actor.queue.QueuePersister
import org.elasticmq.server.config.ElasticMQServerConfig

import java.io.PrintWriter
import scala.collection.mutable

case class QueueConfigStore(config: ElasticMQServerConfig) extends QueuePersister {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  override def persist(queueData: QueueData): Unit = {
    queues.put(queueData.name, queueData)
    saveToConfigFile(queues.values.toList)
  }

  override def remove(queueName: String): Unit = {
    queues.remove(queueName)
    saveToConfigFile(queues.values.toList)
  }

  override def update(queueData: QueueData): Unit = {
    queues.remove(queueData.name)
    queues.put(queueData.name, queueData)
    saveToConfigFile(queues.values.toList)
  }

  private def saveToConfigFile(queues: List[QueueData]): Unit = {
    val queuesConfig: String = queues
      .map(queueDataToConfig)
      .mkString("\n")
    val queueConfig = s"queues {\n $queuesConfig \n}"
    new PrintWriter(config.persisterOutputFile) {
      write(queueConfig)
      close()
    }
  }

  private def queueDataToConfig(queue: QueueData): String = {
    val builder = new StringBuilder()
    builder
      .append(indent(s"${queue.name} {", 2))
      .append("\n")
      .append(indent(s"defaultVisibilityTimeout=${queue.defaultVisibilityTimeout.seconds} seconds", 4))
      .append("\n")
      .append(indent(s"delay=${queue.delay.getStandardSeconds} seconds", 4))
      .append("\n")
      .append(indent(s"receiveMessageWait=${queue.receiveMessageWait.getStandardSeconds} seconds", 4))
      .append("\n")
      .append(indent("deadLettersQueue {", 4))
      .append("\n")
    queue.deadLettersQueue.foreach(deadLetterQueue =>
      builder
        .append(indent(s"name=\"${deadLetterQueue.name}\"", 6))
        .append("\n")
        .append(indent(s"maxReceiveCount=${deadLetterQueue.maxReceiveCount}", 6))
        .append("\n")
    )
    builder
      .append(indent("}",4))
      .append("\n")
      .append(indent(s"fife=${queue.isFifo}",4))
      .append("\n")
      .append(indent(s"contentBasedDeduplication=${queue.hasContentBasedDeduplication}",4))
      .append("\n")
      .append(indent(s"copyTo=\"${queue.copyMessagesTo.getOrElse("")}\"",4))
      .append("\n")
      .append(indent(s"moveTo=\"${queue.moveMessagesTo.getOrElse("")}\"",4))
      .append("\n")
      .append(indent(s"tags {", 4))
      .append("\n")
    queue.tags.foreach(tag => builder.append(indent(s"${tag._1}=\"${tag._2}\"", 6)).append("\n"))
    builder
      .append(indent(s"}", 4))
      .append("\n")
    builder.append(indent(s"}", 2))
      .append("\n")
    builder.toString()
  }

  def indent(value: String, indentation: Int): String = (" " * indentation).concat(value)
}
