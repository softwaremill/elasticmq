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
      .append(s"${queue.name} {".indent(2))
      .append(s"defaultVisibilityTimeout=${queue.defaultVisibilityTimeout.seconds} seconds".indent(4))
      .append(s"delay=${queue.delay.getStandardSeconds} seconds".indent(4))
      .append(s"receiveMessageWait=${queue.receiveMessageWait.getStandardSeconds} seconds".indent(4))
      .append("deadLettersQueue {".indent(4))
    queue.deadLettersQueue.foreach(deadLetterQueue =>
      builder
        .append(s"name=\"${deadLetterQueue.name}\"".indent(6))
        .append(s"maxReceiveCount=${deadLetterQueue.maxReceiveCount}".indent(6))
    )
    builder
      .append("}".indent(4))
      .append(s"fife=${queue.isFifo}".indent(4))
      .append(s"contentBasedDeduplication=${queue.hasContentBasedDeduplication}".indent(4))
      .append(s"copyTo=\"${queue.copyMessagesTo.getOrElse("")}\"".indent(4))
      .append(s"moveTo=\"${queue.moveMessagesTo.getOrElse("")}\"".indent(4))
      .append(s"tags {".indent(4))
    queue.tags.foreach(tag => builder.append(s"${tag._1}=\"${tag._2}\"".indent(6)))
    builder
      .append(s"}".indent(4))
    builder.append(s"}".indent(2))
    builder.toString()
  }
}
