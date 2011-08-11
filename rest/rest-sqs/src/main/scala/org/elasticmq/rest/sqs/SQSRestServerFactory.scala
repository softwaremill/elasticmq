package org.elasticmq.rest.sqs

import org.elasticmq.rest.RestPath._
import org.elasticmq.rest.RestServer
import org.elasticmq.{Queue, Client}

import xml.{Null, UnprefixedAttribute}

object SQSRestServerFactory {
  def start(client: Client, port: Int, baseAddress: String): RestServer = {
    val theClient = client
    val theBaseAddress = baseAddress

    val env = new ClientModule
      with QueueURLModule
      with RequestHandlerLogicModule
      with CreateQueueHandlerModule
      with DeleteQueueHandlerModule
      with QueueAttributesHandlersModule
      with ListQueuesHandlerModule {
      val client = theClient
      val baseAddress = theBaseAddress
    }

    import env._
    RestServer.start(
      createQueueGetHandler :: createQueuePostHandler ::
              deleteQueueGetHandler ::
              listQueuesGetHandler ::
              getQueueAttributesGetHandler ::
              setQueueAttributesGetHandler ::
              Nil, port)
  }
}

object Constants {
  val EMPTY_REQUEST_ID = "00000000-0000-0000-0000-000000000000"
  val SQS_NAMESPACE = new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/2009-02-01/", Null)
  val QUEUE_URL_PATH = "queue"
  val QUEUE_PATH = root / QUEUE_URL_PATH / %("QueueName")
  val QUEUE_NAME_PARAMETER = "QueueName"
}

object ActionUtil {
  def createAction(action: String) = "Action" -> action
}

object ParametersParserUtil {
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

trait ClientModule {
  val client: Client
}

trait QueueURLModule {
  val baseAddress: String

  def queueURL(queue: Queue) = baseAddress+"/"+Constants.QUEUE_URL_PATH+"/"+queue.name
}



