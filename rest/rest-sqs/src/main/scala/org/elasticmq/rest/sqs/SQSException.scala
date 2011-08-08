package org.elasticmq.rest.sqs

class SQSException(message: String, httpStatusCode: Int = 400, errorType: String = "Sender") extends Exception {
  def toXml(requestId: String) =
    <ErrorResponse xmlns="http://queue.amazonaws.com/doc/2009-02-01/">
      <Error>
        <Type>{errorType}</Type>
        <Code>{message}</Code>
        <Message>See the SQS docs.</Message>
        <Detail/>
      </Error>
      <RequestId>{requestId}</RequestId>
    </ErrorResponse>
}

