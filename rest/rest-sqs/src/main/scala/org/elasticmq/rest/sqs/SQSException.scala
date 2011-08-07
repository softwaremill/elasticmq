package org.elasticmq.rest.sqs

import org.jboss.netty.handler.codec.http.HttpRequest
import org.elasticmq.rest.{StringResponse, RequestHandlerLogic}

import SQSConstants._

class SQSException(message: String, httpStatusCode: Int = 400, errorType: String = "Sender") extends Exception {
  def toXml(requestId: String) =
    <ErrorResponse>
      <Error>
        <Type>
          {errorType}
        </Type>
        <Code>
          {message}
        </Code>
        <Message>
          See the SQS docs.
        </Message>
        <Detail/>
      </Error>
      <RequestId>
        {requestId}
      </RequestId>
    </ErrorResponse>
}

trait ExceptionHandlingLogic extends RequestHandlerLogic {
  abstract override def handle(request: HttpRequest, parameters: Map[String, String]) = {
    try {
      super.handle(request, parameters)
    } catch {
      case e: SQSException => StringResponse(e.toXml(emptyRequestId).toString())
    }
  }
}

object ExceptionHandlingLogic {
  def logic(body: (HttpRequest, Map[String, String]) => StringResponse): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = body(request, parameters)
    }

    new TheLogic with ExceptionHandlingLogic
  }
}
