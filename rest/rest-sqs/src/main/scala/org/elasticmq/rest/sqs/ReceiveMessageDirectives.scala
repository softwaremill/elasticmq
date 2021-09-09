package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq._
import org.elasticmq.rest.sqs.MD5Util._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ReceiveMessages
import org.elasticmq.rest.sqs.Action.ReceiveMessage
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.joda.time.Duration

trait ReceiveMessageDirectives {
  this: ElasticMQDirectives with AttributesModule with SQSLimitsModule =>
  object MessageReadeableAttributeNames {
    val SentTimestampAttribute = "SentTimestamp"
    val ApproximateReceiveCountAttribute = "ApproximateReceiveCount"
    val ApproximateFirstReceiveTimestampAttribute =
      "ApproximateFirstReceiveTimestamp"
    val SenderIdAttribute = "SenderId"
    val MaxNumberOfMessagesAttribute = "MaxNumberOfMessages"
    val WaitTimeSecondsAttribute = "WaitTimeSeconds"
    val ReceiveRequestAttemptIdAttribute = "ReceiveRequestAttemptId"
    val MessageAttributeNamePattern = "MessageAttributeName(\\.\\d)?".r
    val MessageDeduplicationIdAttribute = "MessageDeduplicationId"
    val MessageGroupIdAttribute = "MessageGroupId"
    val AWSTraceHeaderAttribute = "AWSTraceHeader"
    val SequenceNumber = "SequenceNumber"

    val AllAttributeNames = SentTimestampAttribute :: ApproximateReceiveCountAttribute ::
      ApproximateFirstReceiveTimestampAttribute :: SenderIdAttribute :: MessageDeduplicationIdAttribute ::
      MessageGroupIdAttribute :: AWSTraceHeaderAttribute :: SequenceNumber :: Nil
  }

  def receiveMessage(p: AnyParams) = {
    import MessageReadeableAttributeNames._

    p.action(ReceiveMessage) {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        val visibilityTimeoutParameterOpt = p.get(VisibilityTimeoutParameter).map(_.toInt)
        val maxNumberOfMessagesAttributeOpt = p.get(MaxNumberOfMessagesAttribute).map(_.toInt)
        val waitTimeSecondsAttributeOpt = p.get(WaitTimeSecondsAttribute).map(_.toLong)

        val receiveRequestAttemptId = p.get(ReceiveRequestAttemptIdAttribute) match {
          // ReceiveRequestAttemptIdAttribute is only supported for FIFO queues
          case Some(v) if !queueData.isFifo =>
            throw SQSException.invalidQueueTypeParameter(v, ReceiveRequestAttemptIdAttribute)

          // Validate values
          case Some(attemptId) if !isValidFifoPropertyValue(attemptId) =>
            throw SQSException.invalidAlphanumericalPunctualParameterValue(attemptId, ReceiveRequestAttemptIdAttribute)

          // The docs at https://docs.aws.amazon.com/cli/latest/reference/sqs/receive-message.html quote:
          //   > If a caller of the receive-message action doesn't provide a ReceiveRequestAttemptId , Amazon SQS
          //   > generates a ReceiveRequestAttemptId .
          // That attempt id doesn't seem to be exposed anywhere however. For now, we will not generate an attempt id
          case a => a
        }

        val visibilityTimeoutFromParameters = visibilityTimeoutParameterOpt
          .map(MillisVisibilityTimeout.fromSeconds(_))
          .getOrElse(DefaultVisibilityTimeout)

        val maxNumberOfMessagesFromParameters =
          maxNumberOfMessagesAttributeOpt.getOrElse(1)

        val waitTimeSecondsFromParameters =
          waitTimeSecondsAttributeOpt.map(Duration.standardSeconds)

        val messageAttributeNames = getMessageAttributeNames(p)

        Limits
          .verifyNumberOfMessagesFromParameters(maxNumberOfMessagesFromParameters, sqsLimits)
          .fold(error => throw new SQSException(error), identity)

        waitTimeSecondsAttributeOpt.foreach(messageWaitTime =>
          Limits
            .verifyMessageWaitTime(messageWaitTime, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
        )

        val msgsFuture = queueActor ? ReceiveMessages(
          visibilityTimeoutFromParameters,
          maxNumberOfMessagesFromParameters,
          waitTimeSecondsFromParameters,
          receiveRequestAttemptId
        )

        val attributeNames = attributeNamesReader.read(p, AllAttributeNames)

        def calculateAttributeValues(msg: MessageData): List[(String, String)] = {
          import AttributeValuesCalculator.Rule

          possiblyEmptyAttributeValuesCalculator.calculate[String](
            attributeNames,
            Rule(SenderIdAttribute, () => Some("127.0.0.1")),
            Rule(SentTimestampAttribute, () => Some(msg.created.getMillis.toString)),
            Rule(ApproximateReceiveCountAttribute, () => Some(msg.statistics.approximateReceiveCount.toString)),
            Rule(MessageDeduplicationIdAttribute, () => msg.messageDeduplicationId.map(_.id)),
            Rule(MessageGroupIdAttribute, () => msg.messageGroupId),
            Rule(
              ApproximateFirstReceiveTimestampAttribute,
              () =>
                Some((msg.statistics.approximateFirstReceive match {
                  case NeverReceived            => 0
                  case OnDateTimeReceived(when) => when.getMillis
                }).toString)
            ),
            Rule(AWSTraceHeaderAttribute, () => msg.tracingId.map(_.id))
          )
        }

        def getFilteredAttributeNames(
            messageAttributeNames: Iterable[String],
            msg: MessageData
        ): Map[String, MessageAttribute] = {
          if (messageAttributeNames.exists(s => s == "All" || s == ".*")) {
            msg.messageAttributes
          } else {
            msg.messageAttributes
              .filterKeys(k => messageAttributeNames.exists(s => s == k || k.r.findFirstIn(s).isDefined))
              .toMap
          }
        }

        msgsFuture.map { msgs =>
          respondWith {
            <ReceiveMessageResponse>
              <ReceiveMessageResult>
                {
              msgs.map { msg =>
                val receipt = msg.deliveryReceipt
                  .map(_.receipt)
                  .getOrElse(throw new RuntimeException("No receipt for a received msg."))
                val filteredMessageAttributes = getFilteredAttributeNames(messageAttributeNames, msg)
                <Message>
                  <MessageId>{msg.id.id}</MessageId>
                  <ReceiptHandle>{receipt}</ReceiptHandle>
                  <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
                  <Body>{XmlUtil.convertTexWithCRToNodeSeq(msg.content)}</Body>
                  {attributesToXmlConverter.convert(calculateAttributeValues(msg))}
                  {
                  if (filteredMessageAttributes.nonEmpty) <MD5OfMessageAttributes>{
                    md5AttributeDigest(filteredMessageAttributes)
                  }</MD5OfMessageAttributes>
                }
                  {messageAttributesToXmlConverter.convert(filteredMessageAttributes.toList)}
                </Message>
              }
            }
              </ReceiveMessageResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </ReceiveMessageResponse>
          }
        }
      }
    }
  }

  def getMessageAttributeNames(p: AnyParams): Iterable[String] = {
    p.filterKeys(k =>
      MessageReadeableAttributeNames.MessageAttributeNamePattern
        .findFirstIn(k)
        .isDefined
    ).values
  }

}
