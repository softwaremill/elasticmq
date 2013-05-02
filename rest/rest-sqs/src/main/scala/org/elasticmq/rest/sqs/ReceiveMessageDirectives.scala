package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq._
import org.elasticmq.rest.sqs.MD5Util._
import annotation.tailrec

trait ReceiveMessageDirectives { this: ElasticMQDirectives with AttributesModule with SQSLimitsModule =>
  object MessageReadeableAttributeNames {
    val SentTimestampAttribute = "SentTimestamp"
    val ApproximateReceiveCountAttribute = "ApproximateReceiveCount"
    val ApproximateFirstReceiveTimestampAttribute = "ApproximateFirstReceiveTimestamp"
    val MaxNumberOfMessagesAttribute = "MaxNumberOfMessages"

    val AllAttributeNames = SentTimestampAttribute :: ApproximateReceiveCountAttribute ::
      ApproximateFirstReceiveTimestampAttribute :: Nil
  }

  val receiveMessage = {
    import MessageReadeableAttributeNames._

    action("ReceiveMessage") {
      queuePath { queue =>
        anyParam(VisibilityTimeoutParameter.as[Int]?, MaxNumberOfMessagesAttribute.as[Int]?) {
          (visibilityTimeoutParameterOpt, maxNumberOfMessagesAttributeOpt) =>

          anyParamsMap { parameters =>
            val visibilityTimeoutFromParameters = visibilityTimeoutParameterOpt
              .map(MillisVisibilityTimeout.fromSeconds(_))
              .getOrElse(DefaultVisibilityTimeout)

            val maxNumberOfMessagesFromParameters = maxNumberOfMessagesAttributeOpt.getOrElse(1)

            ifStrictLimits(maxNumberOfMessagesFromParameters < 1 || maxNumberOfMessagesFromParameters > 10) {
              "ReadCountOutOfRange"
            }

            @tailrec
            def receiveMessages(messagesLeft: Int, received: List[(Message, MessageStatistics)]): List[(Message, MessageStatistics)] = {
              if (messagesLeft == 0) {
                received
              } else {
                queue.receiveMessageWithStatistics(visibilityTimeoutFromParameters) match {
                  case Some(data) => receiveMessages(messagesLeft - 1, data :: received)
                  case None => received
                }
              }
            }

            val msgWithStats = receiveMessages(maxNumberOfMessagesFromParameters, Nil)

            lazy val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)

            def calculateAttributeValues(msg: Message, stats: MessageStatistics): List[(String, String)] = {
              import AttributeValuesCalculator.Rule

              attributeValuesCalculator.calculate(attributeNames,
                Rule(SentTimestampAttribute, ()=>msg.created.getMillis.toString),
                Rule(ApproximateReceiveCountAttribute, ()=>stats.approximateReceiveCount.toString),
                Rule(ApproximateFirstReceiveTimestampAttribute,
                  ()=>(stats.approximateFirstReceive match {
                    case NeverReceived => 0
                    case OnDateTimeReceived(when) => when.getMillis
                  }).toString))
            }

            respondWith {
              <ReceiveMessageResponse>
                <ReceiveMessageResult>
                  {msgWithStats.map { case (msg, stats) =>
                  val receipt = msg.lastDeliveryReceipt.map(_.receipt).getOrElse(throw new RuntimeException("No receipt for a received message."))
                  <Message>
                    <MessageId>{msg.id.id}</MessageId>
                    <ReceiptHandle>{receipt}</ReceiptHandle>
                    <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
                    <Body>{XmlUtil.convertTexWithCRToNodeSeq(msg.content)}</Body>
                    {attributesToXmlConverter.convert(calculateAttributeValues(msg, stats))}
                  </Message> }.toList}
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
  }
}