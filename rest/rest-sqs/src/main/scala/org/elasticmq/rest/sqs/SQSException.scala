package org.elasticmq.rest.sqs

import Constants._

class SQSException(val code: String,
                   val message: String = "See the SQS docs.",
                   val httpStatusCode: Int = 400,
                   errorType: String = "Sender",
                   sqsVersion: String = SqsDefaultVersion) extends Exception {
  def toXml(requestId: String) =
    <ErrorResponse>
      <Error>
        <Type>{errorType}</Type>
        <Code>{code}</Code>
        <Message>{message}</Message>
        <Detail/>
      </Error>
      <RequestId>{requestId}</RequestId>
    </ErrorResponse> % namespaceFor(sqsVersion)

  private def namespaceFor(version: String) = {
    if (version == null || version.isEmpty) { version = SqsDefaultVersion }

    new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null)
  }
}

object SQSException {
  def invalidParameterValue = new SQSException("InvalidParameterValue")
}

