package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.VisibilityTimeout

import Constants._
import ActionUtil._
import ParametersParserUtil._

trait QueueAttributesHandlersModule { this: ClientModule with RequestHandlerLogicModule with AttributesModule =>
  object QueueWriteableAttributeNames {
    val VisibilityTimeoutAttribute = "VisibilityTimeout"
  }

  object QueueReadableAttributeNames {
    val ApproximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"
    val ApproximateNumberOfMessagesNotVisibleAttribute = "ApproximateNumberOfMessagesNotVisible"
    val CreatedTimestampAttribute = "CreatedTimestamp"
    val LastModifiedTimestampAttribute = "LastModifiedTimestamp"

    val AllAttributeNames = QueueWriteableAttributeNames.VisibilityTimeoutAttribute ::
            ApproximateNumberOfMessagesAttribute ::
            ApproximateNumberOfMessagesNotVisibleAttribute ::
            CreatedTimestampAttribute ::
            LastModifiedTimestampAttribute :: Nil
  }

  val getQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    import QueueWriteableAttributeNames._
    import QueueReadableAttributeNames._

    def computeAttributeValues(attributeNames: List[String]) = {
      lazy val stats = client.queueClient.queueStatistics(queue)

      attributeNames.flatMap {
        _ match {
          case VisibilityTimeoutAttribute =>
            Some((VisibilityTimeoutAttribute, queue.defaultVisibilityTimeout.seconds.toString))

          case ApproximateNumberOfMessagesAttribute =>
            Some((ApproximateNumberOfMessagesAttribute, stats.approximateNumberOfVisibleMessages.toString))

          case ApproximateNumberOfMessagesNotVisibleAttribute =>
            Some((ApproximateNumberOfMessagesNotVisibleAttribute, stats.approximateNumberOfInvisibleMessages.toString))

          case CreatedTimestampAttribute =>
            Some((CreatedTimestampAttribute, (queue.created.getMillis/1000L).toString))

          case LastModifiedTimestampAttribute =>
            Some((LastModifiedTimestampAttribute, (queue.lastModified.getMillis/1000L).toString))

          case _ => None
        }
      }
    }

    def responseXml(attributes: List[(String, String)]) = {
      <GetQueueAttributesResponse>
        <GetQueueAttributesResult>
          {attributesToXmlConverter.convert(attributes)}
        </GetQueueAttributesResult>
        <ResponseMetadata>
          <RequestId>{EmptyRequestId}</RequestId>
        </ResponseMetadata>
      </GetQueueAttributesResponse>
    }

    val attributeNames = attributeNamesReader.read(parameters, AllAttributeNames)
    val attributes = computeAttributeValues(attributeNames)
    responseXml(attributes)
  })

  val setQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    val attributeName = parameters(AttributeNameParameter)
    val attributeValue = parameters.parseOptionalLong(AttributeValueParameter).get

    if (attributeName != QueueWriteableAttributeNames.VisibilityTimeoutAttribute) {
      throw new SQSException("InvalidAttributeName")
    }

    client.queueClient.updateDefaultVisibilityTimeout(queue, VisibilityTimeout.fromSeconds(attributeValue))

    <SetQueueAttributesResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SetQueueAttributesResponse>
  })

  val AttributeNameParameter = "Attribute.Name"
  val AttributeValueParameter = "Attribute.Value"

  val GetQueueAttributesAction = createAction("GetQueueAttributes")
  val SetQueueAttribtuesAction = createAction("SetQueueAttributes")

  val getQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(GetQueueAttributesAction)
            running getQueueAttributesLogic)

  val getQueueAttributesPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameterValues Map(GetQueueAttributesAction)
            running getQueueAttributesLogic)

  val setQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameters List(AttributeNameParameter, AttributeValueParameter)
            requiringParameterValues Map(SetQueueAttribtuesAction)
            running setQueueAttributesLogic)

  val setQueueAttributesPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameters List(AttributeNameParameter, AttributeValueParameter)
            requiringParameterValues Map(SetQueueAttribtuesAction)
            running setQueueAttributesLogic)
}