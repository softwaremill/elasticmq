package org.elasticmq
import org.elasticmq.msg.MessageMoveTaskHandle

sealed trait ElasticMQError {
  val queueName: String
  val code: String // TODO: code should be handled in rest-sqs module
  val message: String
}

final case class QueueAlreadyExists(val queueName: String) extends ElasticMQError {
  val code = "QueueAlreadyExists"
  val message = s"Queue already exists: $queueName"
}

final case class InvalidParameterValue(queueName: String, reason: String) extends ElasticMQError {
  val code = "InvalidParameterValue"
  val message = reason
}

final case class InvalidReceiptHandle(val queueName: String, receiptHandle: String) extends ElasticMQError {
  val code = "ReceiptHandleIsInvalid"
  val message = s"""The receipt handle "$receiptHandle" is not valid."""
}

final case class InvalidMessageMoveTaskHandle(val taskHandle: MessageMoveTaskHandle) extends ElasticMQError {
  val code = "ResourceNotFoundException"
  val message = s"""The task handle "$taskHandle" is not valid or does not exist"""

  override val queueName: String = "invalid"
}

final case class MessageMoveTaskAlreadyRunning(val queueName: String) extends ElasticMQError {
  val code = "AWS.SimpleQueueService.UnsupportedOperation"
  val message = s"""A message move task is already running on queue "$queueName""""
}
