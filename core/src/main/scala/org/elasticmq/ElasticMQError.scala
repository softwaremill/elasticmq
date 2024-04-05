package org.elasticmq
import org.elasticmq.msg.MessageMoveTaskHandle

trait ElasticMQError {
  val queueName: String
  val code: String // TODO: code should be handled in rest-sqs module
  val message: String
}

class QueueAlreadyExists(val queueName: String) extends ElasticMQError {
  val code = "QueueAlreadyExists"
  val message = s"Queue already exists: $queueName"
}

case class QueueCreationError(queueName: String, reason: String) extends ElasticMQError {
  val code = "QueueCreationError"
  val message = s"Queue named $queueName could not be created because of $reason"
}

case class InvalidParameterValue(queueName: String, reason: String) extends ElasticMQError {
  val code = "InvalidParameterValue"
  val message = reason
}

class MessageDoesNotExist(val queueName: String, messageId: MessageId) extends ElasticMQError {
  val code = "MessageDoesNotExist"
  val message = s"Message does not exist: $messageId in queue: $queueName"
}

class InvalidReceiptHandle(val queueName: String, receiptHandle: String) extends ElasticMQError {
  val code = "ReceiptHandleIsInvalid"
  val message = s"""The receipt handle "$receiptHandle" is not valid."""
}

class InvalidMessageMoveTaskHandle(val taskHandle: MessageMoveTaskHandle) extends ElasticMQError {
  val code = "ResourceNotFoundException"
  val message = s"""The task handle "$taskHandle" is not valid or does not exist"""

  override val queueName: String = "invalid"
}

class MessageMoveTaskAlreadyRunning(val queueName: String) extends ElasticMQError {
  val code = "AWS.SimpleQueueService.UnsupportedOperation"
  val message = s"""A message move task is already running on queue "$queueName""""
}
