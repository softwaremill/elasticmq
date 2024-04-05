package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.Constants._

import scala.xml.Elem

class SQSException(
    val code: String,
    val httpStatusCode: Int = 400,
    val errorType: String = "Sender",
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

  /** Indicates that a parameter was sent to a queue whose type does not support it */
  def invalidQueueTypeParameter(
      value: String,
      parameterName: String
  ): SQSException =
    invalidParameter(
      value,
      parameterName,
      Some("The request include parameter that is not valid for this queue type")
    )

  /** Indicates that the given value for the given parameter name is not a max 128 alphanumerical (incl punctuation) */
  def invalidAlphanumericalPunctualParameterValue(
      value: String,
      parameterName: String
  ): SQSException =
    invalidParameter(
      value,
      parameterName,
      Some(s"$parameterName can only include alphanumeric and punctuation characters. 1 to 128 in length.")
    )

  /** Indicates that the given value for the given parameter name is invalid */
  def invalidParameter(
      value: String,
      parameterName: String,
      reason: Option[String] = None
  ): SQSException = {
    val valueMessage = s"Value $value for parameter $parameterName is invalid."
    val errorMessage = reason.map(r => s"$valueMessage $r").getOrElse(valueMessage)
    new SQSException(InvalidParameterValueErrorName, errorMessage = Some(errorMessage))
  }

  /** Generic invalid parameter value exception without any further reason */
  def invalidParameterValue: SQSException = new SQSException(InvalidParameterValueErrorName)

  /** Indicates that the request is missing the given parameter name */
  def missingParameter(parameterName: String): SQSException =
    new SQSException(
      MissingParameterName,
      errorMessage = Some(s"The request must contain the parameter $parameterName.")
    )

  /** Indicates a queue does not exist */
  def nonExistentQueue: SQSException =
    new SQSException(
      "AWS.SimpleQueueService.NonExistentQueue",
      errorType = "com.amazonaws.sqs#QueueDoesNotExist",
      errorMessage = Some("The specified queue does not exist.")
    )

  def resourceNotFoundException: SQSException =
    new SQSException(
      "AWS.SimpleQueueService.ResourceNotFoundException",
      errorType = "com.amazonaws.sqs#ResourceNotFoundException",
      errorMessage = Some("One or more specified resources don't exist.")
    )
}
