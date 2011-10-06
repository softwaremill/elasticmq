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

    val messages = client.messageClient.receiveMessage(queue)
    lazy val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)

    def computeAttributeValues(message: SpecifiedMessage): List[(String, String)] = {
      lazy val stats = client.messageClient.messageStatistics(message)

      attributeNames.flatMap {
        _ match {
          case SentTimestampAttribute =>
            Some((SentTimestampAttribute, message.created.getMillis.toString))

          case ApproximateReceiveCountAttribute =>
            Some((ApproximateReceiveCountAttribute, stats.approximateReceiveCount.toString))

          case ApproximateFirstReceiveTimestampAttribute =>
            Some((ApproximateFirstReceiveTimestampAttribute,
                    (stats.approximateFirstReceive match {
                      case NeverReceived => 0
                      case OnDateTimeReceived(when) => when.getMillis
                    }).toString))
        }
      }
    }

    val messagesAttributes: Map[SpecifiedMessage, List[(String, String)]] =
      Map() ++ messages.map(m => { m -> computeAttributeValues(m) }).toList

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
