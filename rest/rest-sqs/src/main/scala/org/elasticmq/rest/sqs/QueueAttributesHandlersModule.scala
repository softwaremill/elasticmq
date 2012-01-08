package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.MillisVisibilityTimeout

import Constants._
import ActionUtil._

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

    def calculateAttributeValues(attributeNames: List[String]) = {
      lazy val stats = client.queueClient.queueStatistics(queue)

      attributeValuesCalculator.calculate(attributeNames,
        (VisibilityTimeoutAttribute, ()=>queue.defaultVisibilityTimeout.seconds.toString),
        (ApproximateNumberOfMessagesAttribute, ()=>stats.approximateNumberOfVisibleMessages.toString),
        (ApproximateNumberOfMessagesNotVisibleAttribute, ()=>stats.approximateNumberOfInvisibleMessages.toString),
        (CreatedTimestampAttribute, ()=>(queue.created.getMillis/1000L).toString),
        (LastModifiedTimestampAttribute, ()=>(queue.lastModified.getMillis/1000L).toString))
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
    val attributes = calculateAttributeValues(attributeNames)
    responseXml(attributes)
  })

  val setQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    val attributes = attributeNameAndValuesReader.read(parameters)
    val firstAttribute = attributes.iterator.next()
    
    if (attributes.size != 1 || firstAttribute._1 != QueueWriteableAttributeNames.VisibilityTimeoutAttribute) {
      throw new SQSException("InvalidAttributeName")
    }

    client.queueClient.updateDefaultVisibilityTimeout(queue,
      MillisVisibilityTimeout.fromSeconds(firstAttribute._2.toLong))

    <SetQueueAttributesResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SetQueueAttributesResponse>
  })

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
            requiringParameterValues Map(SetQueueAttribtuesAction)
            running setQueueAttributesLogic)

  val setQueueAttributesPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameterValues Map(SetQueueAttribtuesAction)
            running setQueueAttributesLogic)
}