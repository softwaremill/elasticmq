package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq._
import org.elasticmq.rest.sqs.MD5Util._
import org.elasticmq.actor.reply._
import org.elasticmq.data.MessageData
import akka.dataflow._
import scala.concurrent.Future
import org.elasticmq.msg.ReceiveMessage

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
      queueActorFromPath { queueActor =>
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

            def receiveMessages(messages: Int): Future[List[MessageData]] = {
              flow {
                if (messages == 0) {
                  Nil
                } else {
                  val msgFuture = queueActor ? ReceiveMessage(System.currentTimeMillis(), visibilityTimeoutFromParameters)
                  val msg = msgFuture()

                  msg match {
                    case Some(data) => {
                      val other = receiveMessages(messages - 1)()
                      data :: other
                    }
                    case None => Nil
                  }
                }
              }
            }

            val msgsFuture = receiveMessages(maxNumberOfMessagesFromParameters)

            lazy val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)

            def calculateAttributeValues(msg: MessageData): List[(String, String)] = {
              import AttributeValuesCalculator.Rule

              attributeValuesCalculator.calculate(attributeNames,
                Rule(SentTimestampAttribute, ()=>msg.created.getMillis.toString),
                Rule(ApproximateReceiveCountAttribute, ()=>msg.statistics.approximateReceiveCount.toString),
                Rule(ApproximateFirstReceiveTimestampAttribute,
                  ()=>(msg.statistics.approximateFirstReceive match {
                    case NeverReceived => 0
                    case OnDateTimeReceived(when) => when.getMillis
                  }).toString))
            }

            msgsFuture.map { msgs =>
              respondWith {
                <ReceiveMessageResponse>
                  <ReceiveMessageResult>
                    {msgs.map { msg =>
                    val receipt = msg.deliveryReceipt.map(_.receipt).getOrElse(throw new RuntimeException("No receipt for a received msg."))
                    <Message>
                      <MessageId>{msg.id.id}</MessageId>
                      <ReceiptHandle>{receipt}</ReceiptHandle>
                      <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
                      <Body>{XmlUtil.convertTexWithCRToNodeSeq(msg.content)}</Body>
                      {attributesToXmlConverter.convert(calculateAttributeValues(msg))}
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
}