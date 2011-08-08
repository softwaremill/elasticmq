package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._
import org.jboss.netty.handler.codec.http.HttpMethod
import org.elasticmq.rest.RestServer
import org.elasticmq.{MillisVisibilityTimeout, Queue, Client}

import HttpMethod._
import SQSRequestHandlingLogic._
import xml.{Null, UnprefixedAttribute}

class SQSRestServerFactory(client: Client, port: Int, baseAddress: String) {
  import SQSUtil._
  import SQSConstants._

  val defaultVisibilityTimeout = 30 * 1000L;

  val queueURLPath = "queue"

  val actionParameter = "Action"
  val defaultVisibilityTimeoutParameter = "DefaultVisibilityTimeout"
  val attributeNameParameter = "Attribute.Name"
  val attributeValueParameter = "Attribute.Value"

  val createQueueAction = actionParameter -> "CreateQueue"
  val deleteQueueAction = actionParameter -> "DeleteQueue"
  val listQueuesAction = actionParameter -> "ListQueues"
  val getQueueAttributesAction = actionParameter -> "GetQueueAttributes"
  val setQueueAttributesAction = actionParameter -> "SetQueueAttributes"

  val createQueueLogic = logic((queueName, request, parameters) => {
    val queueOption = client.queueClient.lookupQueue(queueName)

    val visibilityTimeout = parameters.parseOptionalLong(defaultVisibilityTimeoutParameter)
            .getOrElse(defaultVisibilityTimeout)

    val queue = queueOption.getOrElse(client.queueClient.createQueue(
      Queue(queueName, MillisVisibilityTimeout(visibilityTimeout))))

    if (queue.defaultVisibilityTimeout.millis != visibilityTimeout) {
      // Special case: the queue existed, but has a different visibility timeout
      throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
    }

    <CreateQueueResponse>
      <CreateQueueResult>
        <QueueUrl>{getQueueURL(queue)}</QueueUrl>
      </CreateQueueResult>
      <ResponseMetadata>
        <RequestId>{emptyRequestId}</RequestId>
      </ResponseMetadata>
    </CreateQueueResponse>
  })

  val deleteQueueLogic = logic((queueName, request, parameters) => {
    val queue = getQueue(queueName)
    client.queueClient.deleteQueue(queue)

    <DeleteQueueResponse>
      <ResponseMetadata>
        <RequestId>{emptyRequestId}</RequestId>
      </ResponseMetadata>
    </DeleteQueueResponse>
  })

  val listQueuesLogic = logic((request, parameters) => {
    val prefixOption = parameters.get("QueueNamePrefix")
    val allQueues = client.queueClient.listQueues

    val queues = prefixOption match {
      case Some(prefix) => allQueues.filter(_.name.startsWith(prefix))
      case None => allQueues
    }

    <ListQueuesResponse>
      <ListQueuesResult>
        {queues.map(q => <QueueUrl>{getQueueURL(q)}</QueueUrl>)}
      </ListQueuesResult>
      <ResponseMetadata>
        <RequestId>{emptyRequestId}</RequestId>
      </ResponseMetadata>
    </ListQueuesResponse>
  })

  // So far we support only one attribute ..
  object QueueWriteableAttributeNames {
    val VisibilityTimeout = "VisibilityTimeout"
  }

  val getQueueAttributesLogic = logic((queueName, request, parameters) => {
    val queue = getQueue(queueName)

    def collectAttributeNames(suffix: Int, acc: List[String]): List[String] = {
      parameters.get("AttributeName." + suffix) match {
        case None => acc
        case Some(an) => collectAttributeNames(suffix+1, an :: acc)
      }
    }

    var attributeNames = collectAttributeNames(1, parameters.get("AttributeName").toList)
    if (attributeNames.contains("All")) {
      attributeNames = QueueWriteableAttributeNames.VisibilityTimeout :: Nil
    }

    val attributes = attributeNames.flatMap {
      import QueueWriteableAttributeNames._

      _ match {
        case VisibilityTimeout => Some((VisibilityTimeout, queue.defaultVisibilityTimeout.millis.toString))
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
        <RequestId>{emptyRequestId}</RequestId>
      </ResponseMetadata>
    </GetQueueAttributesResponse>
  })

  val setQueueAttributesLogic = logic((queueName, request, parameters) => {
    val queue = getQueue(queueName)
    val attributeName = parameters(attributeNameParameter)
    val attributeValue = parameters.parseOptionalLong(attributeValueParameter).get

    if (attributeName != QueueWriteableAttributeNames.VisibilityTimeout) {
      throw new SQSException("InvalidAttributeName")
    }

    client.queueClient.updateDefaultVisibilityTimeout(queue, MillisVisibilityTimeout(attributeValue))

    <SetQueueAttributesResponse>
      <ResponseMetadata>
        <RequestId>{emptyRequestId}</RequestId>
      </ResponseMetadata>
    </SetQueueAttributesResponse>
  })

  def getQueue(queueName: String) = {
    val queueOption = client.queueClient.lookupQueue(queueName)

    queueOption match {
      case Some(q) => q
      case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
    }
  }

  def getQueueURL(queue: Queue) = baseAddress+"/"+queueURLPath+"/"+queue.name

  val queuePath = root / queueURLPath / %("QueueName")

  val createQueueGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameters List(queueNameParameter)
            requiringParameterValues Map(createQueueAction)
            running createQueueLogic)

  val createQueuePostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody ()
            requiringParameters List(queueNameParameter)
            requiringParameterValues Map(createQueueAction)
            running createQueueLogic)

  val deleteQueueGetHandler = (createHandler
            forMethod GET
            forPath (queuePath)
            requiringParameterValues Map(deleteQueueAction)
            running deleteQueueLogic)

  val listQueuesGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameterValues Map(listQueuesAction)
            running listQueuesLogic)

  val getQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (queuePath)
            requiringParameterValues Map(getQueueAttributesAction)
            running getQueueAttributesLogic)

  val setQueueAttributesGetHandler = (createHandler
            forMethod GET
            forPath (queuePath)
            requiringParameters List(attributeNameParameter, attributeValueParameter)
            requiringParameterValues Map(setQueueAttributesAction)
            running setQueueAttributesLogic)

  def start(): RestServer = {
    RestServer.start(
      createQueueGetHandler :: createQueuePostHandler ::
              deleteQueueGetHandler ::
              listQueuesGetHandler ::
              getQueueAttributesGetHandler ::
              setQueueAttributesGetHandler ::
              Nil, port)
  }
}

object SQSConstants {
  val emptyRequestId = "00000000-0000-0000-0000-000000000000"

  val sqsNamespace = new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/2009-02-01/", Null)

  val queueNameParameter = "QueueName"
}

object SQSUtil {
  class ParametersParser(parameters: Map[String, String]) {
    def parseOptionalLong(name: String) = {
      val param = parameters.get(name)
      try {
        param.map(_.toLong)
      } catch {
        case e: NumberFormatException => throw new SQSException("InvalidParameterValue")
      }
    }
  }

  implicit def mapToParametersParser(parameters: Map[String, String]): ParametersParser = new ParametersParser(parameters)
}