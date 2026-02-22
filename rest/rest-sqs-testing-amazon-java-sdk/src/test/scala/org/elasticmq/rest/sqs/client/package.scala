package org.elasticmq.rest.sqs

package object client {
  type QueueUrl = String
  type Arn = String
  type TaskHandle = String
  type MessageMoveTaskStatus = String
  type ApproximateNumberOfMessagesMoved = Long
}

package client {
  import org.elasticmq.MessageAttribute
  sealed abstract class QueueAttributeName(val value: String)
  case object AllAttributeNames extends QueueAttributeName("All")
  case object PolicyAttributeName extends QueueAttributeName("Policy")
  case object VisibilityTimeoutAttributeName extends QueueAttributeName("VisibilityTimeout")
  case object MaximumMessageSizeAttributeName extends QueueAttributeName("MaximumMessageSize")
  case object MessageRetentionPeriodAttributeName extends QueueAttributeName("MessageRetentionPeriod")
  case object ApproximateNumberOfMessagesAttributeName extends QueueAttributeName("ApproximateNumberOfMessages")
  case object ApproximateNumberOfMessagesNotVisibleAttributeName
      extends QueueAttributeName("ApproximateNumberOfMessagesNotVisible")
  case object CreatedTimestampAttributeName extends QueueAttributeName("CreatedTimestamp")
  case object LastModifiedTimestampAttributeName extends QueueAttributeName("LastModifiedTimestamp")
  case object QueueArnAttributeName extends QueueAttributeName("QueueArn")
  case object ApproximateNumberOfMessagesDelayedAttributeName
      extends QueueAttributeName("ApproximateNumberOfMessagesDelayed")
  case object DelaySecondsAttributeName extends QueueAttributeName("DelaySeconds")
  case object ReceiveMessageWaitTimeSecondsAttributeName extends QueueAttributeName("ReceiveMessageWaitTimeSeconds")
  case object RedrivePolicyAttributeName extends QueueAttributeName("RedrivePolicy")
  case object FifoQueueAttributeName extends QueueAttributeName("FifoQueue")
  case object ContentBasedDeduplicationAttributeName extends QueueAttributeName("ContentBasedDeduplication")
  case object KmsMasterKeyIdAttributeName extends QueueAttributeName("KmsMasterKeyId")
  case object KmsDataKeyReusePeriodSecondsAttributeName extends QueueAttributeName("KmsDataKeyReusePeriodSeconds")
  case object DeduplicationScopeAttributeName extends QueueAttributeName("DeduplicationScope")
  case object FifoThroughputLimitAttributeName extends QueueAttributeName("FifoThroughputLimit")
  case object RedriveAllowPolicyAttributeName extends QueueAttributeName("RedriveAllowPolicy")
  case object SqsManagedSseEnabledAttributeName extends QueueAttributeName("SqsManagedSseEnabled")

  sealed abstract class MessageSystemAttributeName(val value: String)
  case object SenderId extends MessageSystemAttributeName("SenderId")
  case object SentTimestamp extends MessageSystemAttributeName("SentTimestamp")
  case object ApproximateReceiveCount extends MessageSystemAttributeName("ApproximateReceiveCount")
  case object ApproximateFirstReceiveTimestamp extends MessageSystemAttributeName("ApproximateFirstReceiveTimestamp")
  case object SequenceNumber extends MessageSystemAttributeName("SequenceNumber")
  case object MessageDeduplicationId extends MessageSystemAttributeName("MessageDeduplicationId")
  case object MessageGroupId extends MessageSystemAttributeName("MessageGroupId")
  case object AWSTraceHeader extends MessageSystemAttributeName("AWSTraceHeader")
  case object DeadLetterQueueSourceArn extends MessageSystemAttributeName("DeadLetterQueueSourceArn")

  object MessageSystemAttributeName {
    def from(value: String): MessageSystemAttributeName = {
      value match {
        case SenderId.value                         => SenderId
        case SentTimestamp.value                    => SentTimestamp
        case ApproximateReceiveCount.value          => ApproximateReceiveCount
        case ApproximateFirstReceiveTimestamp.value => ApproximateFirstReceiveTimestamp
        case SequenceNumber.value                   => SequenceNumber
        case MessageDeduplicationId.value           => MessageDeduplicationId
        case MessageGroupId.value                   => MessageGroupId
        case AWSTraceHeader.value                   => AWSTraceHeader
        case DeadLetterQueueSourceArn.value         => DeadLetterQueueSourceArn
        case _ => throw new IllegalArgumentException(s"Unknown message system attribute: $value")
      }
    }
  }

  case class SendMessageResult(
      messageId: String,
      md5OfMessageBody: String,
      md5OfMessageAttributes: Option[String],
      sequenceNumber: Option[String]
  )

  case class SendMessageBatchEntry(
      id: String,
      messageBody: String,
      delaySeconds: Option[Int] = None,
      messageDeduplicationId: Option[String] = None,
      messageGroupId: Option[String] = None,
      awsTraceHeader: Option[String] = None,
      messageAttributes: Map[String, MessageAttribute] = Map.empty
  )

  case class SendMessageBatchSuccessEntry(
      id: String,
      messageId: String,
      md5OfMessageBody: String,
      md5OfMessageAttributes: Option[String],
      md5OfMessageSystemAttributes: Option[String],
      sequenceNumber: Option[String]
  )

  case class BatchOperationErrorEntry(
      id: String,
      senderFault: Boolean,
      code: String,
      message: String
  )

  case class SendMessageBatchResult(
      successful: List[SendMessageBatchSuccessEntry],
      failed: List[BatchOperationErrorEntry]
  )

  case class DeleteMessageBatchEntry(id: String, receiptHandle: String)

  case class DeleteMessageBatchSuccessEntry(id: String)

  case class DeleteMessageBatchResult(
      successful: List[DeleteMessageBatchSuccessEntry],
      failed: List[BatchOperationErrorEntry]
  )

  case class ChangeMessageVisibilityBatchEntry(
      id: String,
      receiptHandle: String,
      visibilityTimeout: Int
  )

  case class ChangeMessageVisibilityBatchSuccessEntry(id: String)

  case class ChangeMessageVisibilityBatchResult(
      successful: List[ChangeMessageVisibilityBatchSuccessEntry],
      failed: List[BatchOperationErrorEntry]
  )

  case class ReceivedMessage(
      messageId: String,
      receiptHandle: String,
      body: String,
      attributes: Map[MessageSystemAttributeName, String],
      messageAttributes: Map[String, MessageAttribute]
  )

  case class MessageMoveTask(
      taskHandle: TaskHandle,
      sourceArn: Arn,
      status: MessageMoveTaskStatus,
      maxNumberOfMessagesPerSecond: Option[Int]
  )

  sealed trait SqsClientErrorType
  case object UnsupportedOperation extends SqsClientErrorType {
    override def toString: String = "UnsupportedOperation"
  }
  case object ResourceNotFound extends SqsClientErrorType {
    override def toString: String = "ResourceNotFound"
  }
  case object QueueDoesNotExist extends SqsClientErrorType {
    override def toString: String = "QueueDoesNotExist"
  }
  case object InvalidParameterValue extends SqsClientErrorType {
    override def toString: String = "InvalidParameterValue"
  }
  case object InvalidAttributeName extends SqsClientErrorType {
    override def toString: String = "InvalidAttributeName"
  }
  case object MissingParameter extends SqsClientErrorType {
    override def toString: String = "MissingParameter"
  }
  case object UnknownSqsClientErrorType extends SqsClientErrorType {
    override def toString: String = "UnknownSqsClientErrorType"
  }
}
