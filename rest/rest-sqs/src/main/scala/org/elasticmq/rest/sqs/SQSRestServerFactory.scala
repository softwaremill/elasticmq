package org.elasticmq.rest.sqs

import org.elasticmq.rest.RestPath._
import org.elasticmq.rest.RestServer
import org.elasticmq.{Queue, Client}

import xml.{Null, UnprefixedAttribute}
import java.security.MessageDigest

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
      with ListQueuesHandlerModule
      with SendMessageHandlerModule
      with ReceiveMessageHandlerModule
      with DeleteMessageHandlerModule
      with ChangeMessageVisibilityHandlerModule
      with AttributesModule {
      val client = theClient
      val baseAddress = theBaseAddress
    }

    import env._
    RestServer.start(
      createQueueGetHandler :: createQueuePostHandler ::
              deleteQueueGetHandler :: deleteQueuePostHandler ::
              listQueuesGetHandler :: listQueuesPostHandler ::
              getQueueAttributesGetHandler :: getQueueAttributesPostHandler ::
              setQueueAttributesGetHandler :: setQueueAttributesPostHandler ::
              sendMessageGetHandler :: sendMessagePostHandler ::
              receiveMessageGetHandler :: receiveMessagePostHandler ::
              deleteMessageGetHandler :: deleteMessagePostHandler ::
              changeMessageVisibilityGetHandler :: changeMessageVisibilityPostHandler ::
              Nil, port)
  }
}

object Constants {
  val EmptyRequestId = "00000000-0000-0000-0000-000000000000"
  val SqsNamespace = new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/2009-02-01/", Null)
  val QueueUrlPath = "queue"
  val QueuePath = root / QueueUrlPath / %("QueueName")
  val QueueNameParameter = "QueueName"
  val ReceiptHandlerParameter = "ReceiptHandle"
  val VisibilityTimeoutParameter = "VisibilityTimeout"
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
        case e: NumberFormatException => throw SQSException.invalidParameterValue
      }
    }
  }

  implicit def mapToParametersParser(parameters: Map[String, String]): ParametersParser = new ParametersParser(parameters)
}

object MD5Util {
  def md5Digest(s: String) = {
    val md5 = MessageDigest.getInstance("MD5")
    md5.reset()
    md5.update(s.getBytes)
    md5.digest().map(0xFF & _).map { "%02x".format(_) }.foldLeft(""){_ + _}
  }
}

trait ClientModule {
  def client: Client
}

trait QueueURLModule {
  def baseAddress: String

  def queueURL(queue: Queue) = baseAddress+"/"+Constants.QueueUrlPath+"/"+queue.name
}



