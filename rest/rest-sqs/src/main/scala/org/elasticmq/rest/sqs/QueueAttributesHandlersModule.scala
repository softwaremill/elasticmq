package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.MillisVisibilityTimeout

import Constants._
import ActionUtil._
import org.joda.time.Duration

trait QueueAttributesHandlersModule { this: ClientModule with RequestHandlerLogicModule with AttributesModule =>
  object QueueWriteableAttributeNames {
    val VisibilityTimeoutAttribute = "VisibilityTimeout"
    val DelaySecondsAttribute = "DelaySeconds"
    
    val AllWriteableAttributeNames = VisibilityTimeoutAttribute :: DelaySecondsAttribute :: Nil
  }

  object QueueReadableAttributeNames {
    val ApproximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"
    val ApproximateNumberOfMessagesNotVisibleAttribute = "ApproximateNumberOfMessagesNotVisible"
    val ApproximateNumberOfMessagesDelayedAttribute = "ApproximateNumberOfMessagesDelayed"
    val CreatedTimestampAttribute = "CreatedTimestamp"
    val LastModifiedTimestampAttribute = "LastModifiedTimestamp"

    val AllAttributeNames = QueueWriteableAttributeNames.AllWriteableAttributeNames ++
            (ApproximateNumberOfMessagesAttribute ::
            ApproximateNumberOfMessagesNotVisibleAttribute ::
            ApproximateNumberOfMessagesDelayedAttribute ::
            CreatedTimestampAttribute ::
            LastModifiedTimestampAttribute :: Nil)
  }

  val getQueueAttributesLogic = logicWithQueue((queue, request, parameters) => {
    import QueueWriteableAttributeNames._
    import QueueReadableAttributeNames._

    def calculateAttributeValues(attributeNames: List[String]) = {
      lazy val stats = queue.fetchStatistics()

      import AttributeValuesCalculator.Rule

      attributeValuesCalculator.calculate(attributeNames,
        Rule(VisibilityTimeoutAttribute, ()=>queue.defaultVisibilityTimeout.seconds.toString),
        Rule(DelaySecondsAttribute, ()=>queue.delay.getStandardSeconds.toString),
        Rule(ApproximateNumberOfMessagesAttribute, ()=>stats.approximateNumberOfVisibleMessages.toString),
        Rule(ApproximateNumberOfMessagesNotVisibleAttribute, ()=>stats.approximateNumberOfInvisibleMessages.toString),
        Rule(ApproximateNumberOfMessagesDelayedAttribute, ()=>stats.approximateNumberOfMessagesDelayed.toString),
        Rule(CreatedTimestampAttribute, ()=>(queue.created.getMillis/1000L).toString),
        Rule(LastModifiedTimestampAttribute, ()=>(queue.lastModified.getMillis/1000L).toString))
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

    attributes.foreach({ case (attributeName, attributeValue) =>
      attributeName match {
        case QueueWriteableAttributeNames.VisibilityTimeoutAttribute => {
          queue.updateDefaultVisibilityTimeout(
            MillisVisibilityTimeout.fromSeconds(attributeValue.toLong))
        }
        case QueueWriteableAttributeNames.DelaySecondsAttribute => {
          queue.updateDelay(Duration.standardSeconds(attributeValue.toLong))
        }
        case _ => throw new SQSException("InvalidAttributeName")
      }
    })

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

trait QueueAttributesDirectives { this: ElasticMQDirectives with AttributesModule =>
  object QueueWriteableAttributeNames {
    val VisibilityTimeoutAttribute = "VisibilityTimeout"
    val DelaySecondsAttribute = "DelaySeconds"

    val AllWriteableAttributeNames = VisibilityTimeoutAttribute :: DelaySecondsAttribute :: Nil
  }

  object QueueReadableAttributeNames {
    val ApproximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"
    val ApproximateNumberOfMessagesNotVisibleAttribute = "ApproximateNumberOfMessagesNotVisible"
    val ApproximateNumberOfMessagesDelayedAttribute = "ApproximateNumberOfMessagesDelayed"
    val CreatedTimestampAttribute = "CreatedTimestamp"
    val LastModifiedTimestampAttribute = "LastModifiedTimestamp"

    val AllAttributeNames = QueueWriteableAttributeNames.AllWriteableAttributeNames ++
      (ApproximateNumberOfMessagesAttribute ::
        ApproximateNumberOfMessagesNotVisibleAttribute ::
        ApproximateNumberOfMessagesDelayedAttribute ::
        CreatedTimestampAttribute ::
        LastModifiedTimestampAttribute :: Nil)
  }

  val getQueueAttributes = {
    action("GetQueueAttributes") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          import QueueWriteableAttributeNames._
          import QueueReadableAttributeNames._

          def calculateAttributeValues(attributeNames: List[String]) = {
            lazy val stats = queue.fetchStatistics()

            import AttributeValuesCalculator.Rule

            attributeValuesCalculator.calculate(attributeNames,
              Rule(VisibilityTimeoutAttribute, ()=>queue.defaultVisibilityTimeout.seconds.toString),
              Rule(DelaySecondsAttribute, ()=>queue.delay.getStandardSeconds.toString),
              Rule(ApproximateNumberOfMessagesAttribute, ()=>stats.approximateNumberOfVisibleMessages.toString),
              Rule(ApproximateNumberOfMessagesNotVisibleAttribute, ()=>stats.approximateNumberOfInvisibleMessages.toString),
              Rule(ApproximateNumberOfMessagesDelayedAttribute, ()=>stats.approximateNumberOfMessagesDelayed.toString),
              Rule(CreatedTimestampAttribute, ()=>(queue.created.getMillis/1000L).toString),
              Rule(LastModifiedTimestampAttribute, ()=>(queue.lastModified.getMillis/1000L).toString))
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

          respondWith {
            responseXml(attributes)
          }
        }
      }
    }
  }

  val setQueueAttributes = {
    action("SetQueueAttributes") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          val attributes = attributeNameAndValuesReader.read(parameters)

          attributes.foreach({ case (attributeName, attributeValue) =>
            attributeName match {
              case QueueWriteableAttributeNames.VisibilityTimeoutAttribute => {
                queue.updateDefaultVisibilityTimeout(
                  MillisVisibilityTimeout.fromSeconds(attributeValue.toLong))
              }
              case QueueWriteableAttributeNames.DelaySecondsAttribute => {
                queue.updateDelay(Duration.standardSeconds(attributeValue.toLong))
              }
              case _ => throw new SQSException("InvalidAttributeName")
            }
          })

          respondWith {
            <SetQueueAttributesResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </SetQueueAttributesResponse>
          }
        }
      }
    }
  }
}