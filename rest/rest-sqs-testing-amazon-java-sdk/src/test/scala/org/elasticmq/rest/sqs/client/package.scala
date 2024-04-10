package org.elasticmq.rest.sqs

package object client {
  type QueueUrl = String
  type Arn = String
  type TaskHandle = String
  type MessageMoveTaskStatus = String
  type ApproximateNumberOfMessagesMoved = Long
}

package client {
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

  case class ReceivedMessage(messageId: String, receiptHandle: String, body: String)

  case class MessageMoveTask(
      taskHandle: TaskHandle,
      sourceArn: Arn,
      status: MessageMoveTaskStatus,
      maxNumberOfMessagesPerSecond: Option[Int]
  )

  sealed trait SqsClientErrorType
  case object UnsupportedOperation extends SqsClientErrorType
  case object ResourceNotFound extends SqsClientErrorType
  case object QueueDoesNotExist extends SqsClientErrorType
  case object UnknownSqsClientErrorType extends SqsClientErrorType
}
