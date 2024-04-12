package org.elasticmq
import org.elasticmq.msg.MessageMoveTaskHandle

sealed trait ElasticMQError {
  val queueName: String
  val message: String
}

final case class QueueAlreadyExists(queueName: String) extends ElasticMQError {
  val message = s"Queue already exists: $queueName"
}

final case class InvalidParameterValue(queueName: String, reason: String) extends ElasticMQError {
  val message = reason
}

final case class InvalidReceiptHandle(queueName: String, receiptHandle: String) extends ElasticMQError {
  val message = s"""The receipt handle "$receiptHandle" is not valid."""
}

final case class InvalidMessageMoveTaskHandle(taskHandle: MessageMoveTaskHandle) extends ElasticMQError {
  val message = s"""The task handle "$taskHandle" is not valid or does not exist"""

  override val queueName: String = "invalid"
}

final case class MessageMoveTaskAlreadyRunning(queueName: String) extends ElasticMQError {
  val message = s"""A message move task is already running on queue "$queueName""""
}
