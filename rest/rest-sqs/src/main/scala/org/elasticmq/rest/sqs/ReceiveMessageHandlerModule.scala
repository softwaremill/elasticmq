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

    val messagesStatistics = client.messageClient.receiveMessageWithStatistics(queue, calculateVisibilityTimeout())
    val messages = messagesStatistics.map(_.message)
    lazy val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)

    def calculateAttributeValues(stats: MessageStatistics): List[(String, String)] = {
      attributeValuesCalculator.calculate(attributeNames,
        (SentTimestampAttribute, ()=>stats.message.created.getMillis.toString),
        (ApproximateReceiveCountAttribute, ()=>stats.approximateReceiveCount.toString),
        (ApproximateFirstReceiveTimestampAttribute,
          ()=>(stats.approximateFirstReceive match {
            case NeverReceived => 0
            case OnDateTimeReceived(when) => when.getMillis
          }).toString))
    }

    val messagesAttributes: Map[SpecifiedMessage, List[(String, String)]] =
      Map() ++ messagesStatistics.map(s => { s.message -> calculateAttributeValues(s) }).toList

    <ReceiveMessageResponse>
      <ReceiveMessageResult>
        {messages.map(m =>
        <Message>
          <MessageId>{m.id.get}</MessageId>
          <ReceiptHandle>{m.id.get}</ReceiptHandle>
          <MD5OfBody>{md5Digest(m.content)}</MD5OfBody>
          <Body>{m.content}</Body>
          {attributesToXmlConverter.convert(messagesAttributes(m))}
        </Message>).toList}
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
