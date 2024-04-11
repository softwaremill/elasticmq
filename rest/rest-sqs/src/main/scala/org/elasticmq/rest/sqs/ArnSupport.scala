package org.elasticmq.rest.sqs

trait ArnSupport {

  private val ArnPattern = "(?:.+:(.+)?:(.+)?:)?(.+)".r

  def extractQueueName(arn: String): String =
    arn match {
      case ArnPattern(_, _, queueName) => queueName
      case _                           => throw SQSException.invalidAttributeValue()
    }
}
