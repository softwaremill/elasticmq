package org.elasticmq.rest.sqs

import xml.UnprefixedAttribute

class SQSException(val code: String,
                   val message: String = "See the SQS docs.",
                   val httpStatusCode: Int = 400,
                   errorType: String = "Sender") extends Exception {
  def toXml(requestId: String, namespaceAttribute: UnprefixedAttribute) =
    <ErrorResponse>
      <Error>
        <Type>{errorType}</Type>
        <Code>{code}</Code>
        <Message>{message}</Message>
        <Detail/>
      </Error>
      <RequestId>{requestId}</RequestId>
    </ErrorResponse> % namespaceAttribute
}

object SQSException {
  def invalidParameterValue = new SQSException("InvalidParameterValue")
}

