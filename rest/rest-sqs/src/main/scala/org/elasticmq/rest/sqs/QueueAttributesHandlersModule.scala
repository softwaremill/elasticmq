package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.VisibilityTimeout

import Constants._
import ActionUtil._
import ParametersParserUtil._

trait QueueAttributesHandlersModule { this: ClientModule with RequestHandlerLogicModule =>
  import QueueAttributesHandlersModule._

  // So far we support only one attribute ..
  object QueueWriteableAttributeNames {
    val VisibilityTimeoutAttribute = "VisibilityTimeout"
  }

  val getQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    def collectAttributeNames(suffix: Int, acc: List[String]): List[String] = {
      parameters.get("AttributeName." + suffix) match {
        case None => acc
        case Some(an) => collectAttributeNames(suffix+1, an :: acc)
      }
    }

    var attributeNames = collectAttributeNames(1, parameters.get("AttributeName").toList)
    if (attributeNames.contains("All")) {
      attributeNames = QueueWriteableAttributeNames.VisibilityTimeoutAttribute :: Nil
    }

    val attributes = attributeNames.flatMap {
      import QueueWriteableAttributeNames._

      _ match {
        case VisibilityTimeoutAttribute => Some((VisibilityTimeoutAttribute, (queue.defaultVisibilityTimeout.millis / 1000).toString))
        case _ => None
      }
    }

    <GetQueueAttributesResponse>
      <GetQueueAttributesResult>
        {attributes.map(a =>
        <Attribute>
          <Name>{a._1}</Name>
          <Value>{a._2}</Value>
        </Attribute>)}
      </GetQueueAttributesResult>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </GetQueueAttributesResponse>
  })

  val setQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    val attributeName = parameters(ATTRIBUTE_NAME_PARAMETER)
    val attributeValue = parameters.parseOptionalLong(ATTRIBUTE_VALUE_PARAMETER).get

    if (attributeName != QueueWriteableAttributeNames.VisibilityTimeoutAttribute) {
      throw new SQSException("InvalidAttributeName")
    }

    client.queueClient.updateDefaultVisibilityTimeout(queue, VisibilityTimeout(attributeValue))

    <SetQueueAttributesResponse>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </SetQueueAttributesResponse>
  })

  val getQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (QUEUE_PATH)
            requiringParameterValues Map(GET_QUEUE_ATTRIBUTES_ACTION)
            running getQueueAttributesLogic)

  val getQueueAttributesPostHandler = (createHandler
            forMethod POST
            forPath (QUEUE_PATH)
            includingParametersFromBody()
            requiringParameterValues Map(GET_QUEUE_ATTRIBUTES_ACTION)
            running getQueueAttributesLogic)

  val setQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (QUEUE_PATH)
            requiringParameters List(ATTRIBUTE_NAME_PARAMETER, ATTRIBUTE_VALUE_PARAMETER)
            requiringParameterValues Map(SET_QUEUE_ATTRIBUTES_ACTION)
            running setQueueAttributesLogic)

  val setQueueAttributesPostHandler = (createHandler
            forMethod POST
            forPath (QUEUE_PATH)
            includingParametersFromBody()
            requiringParameters List(ATTRIBUTE_NAME_PARAMETER, ATTRIBUTE_VALUE_PARAMETER)
            requiringParameterValues Map(SET_QUEUE_ATTRIBUTES_ACTION)
            running setQueueAttributesLogic)
}

object QueueAttributesHandlersModule {
  val ATTRIBUTE_NAME_PARAMETER = "Attribute.Name"
  val ATTRIBUTE_VALUE_PARAMETER = "Attribute.Value"

  val GET_QUEUE_ATTRIBUTES_ACTION = createAction("GetQueueAttributes")
  val SET_QUEUE_ATTRIBUTES_ACTION = createAction("SetQueueAttributes")
}