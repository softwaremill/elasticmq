package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import org.elasticmq._
import org.elasticmq.rest.sqs.ActionUtil._
import org.elasticmq.rest.sqs.MD5Util._

trait ReceiveMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule with AttributesModule =>
  object MessageReadeableAttributeNames {
    val SentTimestampAttribute = "SentTimestamp"
    val ApproximateReceiveCountAttribute = "ApproximateReceiveCount"
    val ApproximateFirstReceiveTimestampAttribute = "ApproximateFirstReceiveTimestamp"

    val AllAttributeNames = SentTimestampAttribute :: ApproximateReceiveCountAttribute ::
            ApproximateFirstReceiveTimestampAttribute :: Nil
  }

  val receiveMessageLogic = logicWithQueue((queue, request, parameters) => {
    import MessageReadeableAttributeNames._

    def calculateVisibilityTimeout() = {
      parameters.get(VisibilityTimeoutParameter) match {
        case Some(vt) => MillisVisibilityTimeout.fromSeconds(vt.toInt)
        case None => DefaultVisibilityTimeout
      }
    }

    val msgWithStats = queue.receiveMessageWithStatistics(calculateVisibilityTimeout())

    lazy val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)

    def calculateAttributeValues(msg: Message, stats: MessageStatistics): List[(String, String)] = {
      attributeValuesCalculator.calculate(attributeNames,
        (SentTimestampAttribute, ()=>msg.created.getMillis.toString),
        (ApproximateReceiveCountAttribute, ()=>stats.approximateReceiveCount.toString),
        (ApproximateFirstReceiveTimestampAttribute,
          ()=>(stats.approximateFirstReceive match {
            case NeverReceived => 0
            case OnDateTimeReceived(when) => when.getMillis
          }).toString))
    }

    <ReceiveMessageResponse>
      <ReceiveMessageResult>
        {msgWithStats.map { case (msg, stats) =>
        <Message>
          <MessageId>{msg.id.id}</MessageId>
          <ReceiptHandle>{msg.id.id}</ReceiptHandle>
          <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
          <Body>{msg.content}</Body>
          {attributesToXmlConverter.convert(calculateAttributeValues(msg, stats))}
        </Message> }.toList}
      </ReceiveMessageResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </ReceiveMessageResponse>
  })

  val ReceiveMessageAction = createAction("ReceiveMessage")

  val receiveMessageGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(ReceiveMessageAction)
            running receiveMessageLogic)

  val receiveMessagePostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameterValues Map(ReceiveMessageAction)
            running receiveMessageLogic)
}
