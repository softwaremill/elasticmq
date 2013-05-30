package org.elasticmq.rest.sqs

class SQSException(val code: String,
                   val httpStatusCode: Int = 400,
                   errorType: String = "Sender") extends Exception {
  val message: String = code + "; see the SQS docs."

  def toXml(requestId: String) =
    <ErrorResponse>
      <Error>
        <Type>{errorType}</Type>
        <Code>{code}</Code>
        <Message>{message}</Message>
        <Detail/>
      </Error>
      <RequestId>{requestId}</RequestId>
    </ErrorResponse>
}

object SQSException {
  def invalidParameterValue = new SQSException("InvalidParameterValue")
}

