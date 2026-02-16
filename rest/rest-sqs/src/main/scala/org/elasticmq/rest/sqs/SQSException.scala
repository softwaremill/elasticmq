package org.elasticmq.rest.sqs

import org.elasticmq.{
  ElasticMQError,
  InvalidMessageMoveTaskHandle,
  InvalidParameterValue,
  InvalidReceiptHandle,
  MessageMoveTaskAlreadyRunning,
  QueueAlreadyExists
}

import scala.xml.Elem

class SQSException(
    val code: String,
    val httpStatusCode: Int = 400,
    val errorType: String,
    errorMessage: Option[String] = None
) extends Exception {
  val message: String = errorMessage.getOrElse(code + "; see the SQS docs.")

  def toXml(requestId: String): Elem =
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

  def invalidAction(errorMessage: String): SQSException = {
    new SQSException(
      "InvalidAction",
      errorType = "com.amazonaws.sqs#InvalidAction",
      errorMessage = Some(errorMessage)
    )
  }

  def missingAction: SQSException = {
    new SQSException(
      "MissingAction",
      errorType = "com.amazonaws.sqs#MissingAction",
      errorMessage = Some("Action is missing in the request parameters")
    )
  }

  /** Indicates that a parameter was sent to a queue whose type does not support it */
  def invalidQueueTypeParameter(
      parameterName: String
  ): SQSException =
    invalidParameter(s"The request includes parameter $parameterName that is not valid for this queue type")

  /** Indicates that the given value for the given parameter name is not a max 128 alphanumerical (incl punctuation) */
  def invalidAlphanumericalPunctualParameterValue(
      parameterName: String
  ): SQSException =
    invalidParameter(s"$parameterName can only include alphanumeric and punctuation characters. 1 to 128 in length.")

  /** Indicates that the given value for the given parameter name is invalid */
  def invalidParameter(
      value: String,
      parameterName: String
  ): SQSException = {
    val errorMessage = s"Value $value for parameter $parameterName is invalid."
    new SQSException(
      "InvalidAttributeValue",
      errorType = "com.amazonaws.sqs#InvalidAttributeValue",
      errorMessage = Some(errorMessage)
    )
  }

  def invalidParameter(errorMessage: String): SQSException = {
    new SQSException(
      "InvalidParameterValue",
      errorType = "com.amazonaws.sqs#InvalidParameterValue",
      errorMessage = Some(errorMessage)
    )
  }

  def invalidParameterValue: SQSException = new SQSException(
    "InvalidParameterValue",
    errorType = "com.amazonaws.sqs#InvalidParameterValue",
    errorMessage = Some("The specified parameter value is invalid.")
  )

  def invalidAttributeName(name: String): SQSException = new SQSException(
    "InvalidAttributeName",
    errorType = "com.amazonaws.sqs#InvalidAttributeName",
    errorMessage = Some(s"The attribute $name is invalid.")
  )

  def invalidAttributeValue(name: Option[String] = None): SQSException = new SQSException(
    "InvalidAttributeValue",
    errorType = "com.amazonaws.sqs#InvalidAttributeValue",
    errorMessage = Some(
      name.map(n => s"The attribute value for $n is invalid.").getOrElse("The specified attribute value is invalid.")
    )
  )

  def invalidAttributeValue(name: String, errorMessage: Option[String]): SQSException = new SQSException(
    "InvalidAttributeValue",
    errorType = "com.amazonaws.sqs#InvalidAttributeValue",
    errorMessage = Some(errorMessage.getOrElse(s"The attribute value for $name is invalid."))
  )

  /** Indicates that the request is missing the given parameter name */
  def missingParameter(parameterName: String): SQSException =
    new SQSException(
      "MissingParameter",
      errorType = "MissingParameter",
      errorMessage = Some(s"The request must contain the parameter $parameterName.")
    )

  /** Indicates a queue does not exist */
  def nonExistentQueue: SQSException =
    new SQSException(
      "AWS.SimpleQueueService.NonExistentQueue",
      errorType = "com.amazonaws.sqs#QueueDoesNotExist",
      errorMessage = Some("The specified queue does not exist.")
    )

  def batchEntryIdsNotDistinct: SQSException = new SQSException(
    "AWS.SimpleQueueService.BatchEntryIdsNotDistinct",
    errorType = "com.amazonaws.sqs#BatchEntryIdsNotDistinct",
    errorMessage = Some("BatchEntryIdsNotDistinct")
  )

  def invalidClientTokenId(message: String): SQSException = {
    new SQSException(
      "InvalidClientTokenId",
      403,
      "AuthFailure",
      Some(message)
    )
  }

  def tooManyEntriesInBatchRequest: SQSException = new SQSException(
    "AWS.SimpleQueueService.TooManyEntriesInBatchRequest",
    errorType = "com.amazonaws.sqs#TooManyEntriesInBatchRequest",
    errorMessage = Some("TooManyEntriesInBatchRequest")
  )

  implicit class ElasticMQErrorOps(e: ElasticMQError) {
    def toSQSException: SQSException = {
      val (v1Code, v2Code) = e match {
        case _: QueueAlreadyExists            => ("QueueAlreadyExists", "QueueNameExists")
        case _: InvalidParameterValue         => ("InvalidAttributeName", "InvalidAttributeName")
        case _: InvalidReceiptHandle          => ("ReceiptHandleIsInvalid", "ReceiptHandleIsInvalid")
        case _: InvalidMessageMoveTaskHandle  => ("ResourceNotFoundException", "ResourceNotFoundException")
        case _: MessageMoveTaskAlreadyRunning => ("AWS.SimpleQueueService.UnsupportedOperation", "UnsupportedOperation")
      }
      new SQSException(v1Code, errorType = "com.amazonaws.sqs#" + v2Code, errorMessage = Some(e.message))
    }
  }
}
