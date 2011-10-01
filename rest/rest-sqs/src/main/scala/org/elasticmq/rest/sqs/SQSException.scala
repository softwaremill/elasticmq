package org.elasticmq.rest.sqs

import Constants._

class SQSException(message: String, httpStatusCode: Int = 400, errorType: String = "Sender") extends Exception {
  def toXml(requestId: String) =
    <ErrorResponse>
      <Error>
        <Type>{errorType}</Type>
        <Code>{message}</Code>
        <Message>See the SQS docs.</Message>
        <Detail/>
      </Error>
      <RequestId>{requestId}</RequestId>
    </ErrorResponse> % SQS_NAMESPACE
}

object SQSException {
  def invalidParameterValue = new SQSException("InvalidParameterValue")
}

