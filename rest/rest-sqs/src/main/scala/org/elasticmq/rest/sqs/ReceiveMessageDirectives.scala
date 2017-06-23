package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq._
import org.elasticmq.rest.sqs.MD5Util._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ReceiveMessages
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.joda.time.Duration

trait ReceiveMessageDirectives { this: ElasticMQDirectives with AttributesModule with SQSLimitsModule =>
  object MessageReadeableAttributeNames {
    val SentTimestampAttribute = "SentTimestamp"
    val ApproximateReceiveCountAttribute = "ApproximateReceiveCount"
    val ApproximateFirstReceiveTimestampAttribute = "ApproximateFirstReceiveTimestamp"
    val SenderIdAttribute = "SenderId"
    val MaxNumberOfMessagesAttribute = "MaxNumberOfMessages"
    val WaitTimeSecondsAttribute = "WaitTimeSeconds"
    val MessageAttributeNamePattern = "MessageAttributeName\\.\\d".r

    val AllAttributeNames = SentTimestampAttribute :: ApproximateReceiveCountAttribute ::
      ApproximateFirstReceiveTimestampAttribute :: SenderIdAttribute :: Nil
  }

  def receiveMessage(p: AnyParams) = {
    import MessageReadeableAttributeNames._

    p.action("ReceiveMessage") {
      queueActorFromRequest(p) { queueActor =>
        val visibilityTimeoutParameterOpt = p.get(VisibilityTimeoutParameter).map(_.toInt)
        val maxNumberOfMessagesAttributeOpt = p.get(MaxNumberOfMessagesAttribute).map(_.toInt)
        val waitTimeSecondsAttributeOpt = p.get(WaitTimeSecondsAttribute).map(_.toLong)

        val visibilityTimeoutFromParameters = visibilityTimeoutParameterOpt
          .map(MillisVisibilityTimeout.fromSeconds(_))
          .getOrElse(DefaultVisibilityTimeout)

        val maxNumberOfMessagesFromParameters = maxNumberOfMessagesAttributeOpt.getOrElse(1)

        val waitTimeSecondsFromParameters = waitTimeSecondsAttributeOpt.map(Duration.standardSeconds)

        val messageAttributeNames = getMessageAttributeNames(p)

        ifStrictLimits(maxNumberOfMessagesFromParameters < 1 || maxNumberOfMessagesFromParameters > 10) {
          "ReadCountOutOfRange"
        }

        verifyMessageWaitTime(waitTimeSecondsAttributeOpt)

        val msgsFuture = queueActor ? ReceiveMessages( visibilityTimeoutFromParameters,
          maxNumberOfMessagesFromParameters,
          waitTimeSecondsFromParameters)

        lazy val attributeNames = attributeNamesReader.read(p, AllAttributeNames)

        def calculateAttributeValues(msg: MessageData): List[(String, String)] = {
          import AttributeValuesCalculator.Rule

          attributeValuesCalculator.calculate(attributeNames,
            Rule(SenderIdAttribute, ()=> "127.0.0.1"),
            Rule(SentTimestampAttribute, ()=>msg.created.getMillis.toString),
            Rule(ApproximateReceiveCountAttribute, ()=>msg.statistics.approximateReceiveCount.toString),
            Rule(ApproximateFirstReceiveTimestampAttribute,
              ()=>(msg.statistics.approximateFirstReceive match {
                case NeverReceived => 0
                case OnDateTimeReceived(when) => when.getMillis
              }).toString))
        }

        def getFilteredAttributeNames(messageAttributeNames: Iterable[String], msg: MessageData) = {
          if (messageAttributeNames.exists(s => s == "All" || s == ".*")) {
            msg.messageAttributes
          } else {
            msg.messageAttributes.filterKeys(k => messageAttributeNames.exists(s => s == k || k.r.findFirstIn(s).isDefined))
          }
        }

        msgsFuture.map { msgs =>
          respondWith {
            <ReceiveMessageResponse>
              <ReceiveMessageResult>
                {msgs.map { msg =>
                val receipt = msg.deliveryReceipt.map(_.receipt).getOrElse(throw new RuntimeException("No receipt for a received msg."))
                val filteredMessageAttributes = getFilteredAttributeNames(messageAttributeNames, msg)
                <Message>
                  <MessageId>{msg.id.id}</MessageId>
                  <ReceiptHandle>{receipt}</ReceiptHandle>
                  <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
                  <Body>{XmlUtil.convertTexWithCRToNodeSeq(msg.content)}</Body>
                  {attributesToXmlConverter.convert(calculateAttributeValues(msg))}
                  {if (!filteredMessageAttributes.isEmpty) <MD5OfMessageAttributes>{md5AttributeDigest(filteredMessageAttributes)}</MD5OfMessageAttributes>}
                  {messageAttributesToXmlConverter.convert(filteredMessageAttributes.toList)}
                </Message> }}
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
    p.filterKeys(k => MessageReadeableAttributeNames.MessageAttributeNamePattern.findFirstIn(k).isDefined).values
  }

}