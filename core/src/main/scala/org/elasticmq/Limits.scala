package org.elasticmq

import scala.collection.JavaConverters._
import scala.util.Try

trait Limits {
  def queueNameLengthLimit: Limit[Int]
  def batchSizeLimit: Limit[Int]
  def numberOfMessagesLimit: Limit[RangeLimit[Int]]
  def nonEmptyAttributesLimit: Limit[Boolean]
  def bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]]
  def numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]]
  def messageWaitTimeLimit: Limit[RangeLimit[Long]]
  def maxMessageLength: Limit[Int]
  def messageAttributesLimit: Limit[Int]
}

case object StrictSQSLimits extends Limits {
  override val queueNameLengthLimit: Limit[Int] = LimitedValue(80)
  override val batchSizeLimit: Limit[Int] = LimitedValue(10)
  override val numberOfMessagesLimit: Limit[RangeLimit[Int]] = LimitedValue(RangeLimit(1, 10))
  override val nonEmptyAttributesLimit: Limit[Boolean] = LimitedValue(true)
  override val messageAttributesLimit: Limit[Int] = LimitedValue(10)
  override val bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]] = LimitedValue(
    List(
      RangeLimit(0x9, 0x9),
      RangeLimit(0xa, 0xa),
      RangeLimit(0xd, 0xd),
      RangeLimit(0x20, 0xd7ff),
      RangeLimit(0xe000, 0xfffd),
      RangeLimit(0x10000, 0x10ffff)
    )
  )
  override val numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]] = LimitedValue(
    RangeLimit(-BigDecimal.valueOf(10).pow(128), BigDecimal.valueOf(10).pow(126))
  )
  override val messageWaitTimeLimit: Limit[RangeLimit[Long]] = LimitedValue(RangeLimit(0L, 20L))
  override val maxMessageLength: Limit[Int] = LimitedValue(1024 * 1024)
}
case object RelaxedSQSLimits extends Limits {
  override val queueNameLengthLimit: Limit[Int] = NoLimit
  override val batchSizeLimit: Limit[Int] = NoLimit
  override val numberOfMessagesLimit: Limit[RangeLimit[Int]] = NoLimit
  override val nonEmptyAttributesLimit: Limit[Boolean] = NoLimit
  override val bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]] = NoLimit
  override val numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]] = NoLimit
  override val messageWaitTimeLimit: Limit[RangeLimit[Long]] = NoLimit
  override val maxMessageLength: Limit[Int] = NoLimit
  override val messageAttributesLimit: Limit[Int] = NoLimit
}

sealed trait Limit[+A]
case class LimitedValue[A](value: A) extends Limit[A]
case object NoLimit extends Limit[Nothing]

case class RangeLimit[A](from: A, to: A)(implicit ord: Ordering[A]) {
  def isBetween(value: A): Boolean = ord.gteq(value, from) && ord.lteq(value, to)
}

object Limits {

  def verifyBatchSize(batchSize: Int, limits: Limits): Either[String, Unit] =
    validateWhenLimitAvailable(limits.batchSizeLimit)(
      limit => batchSize <= limit,
      "Too many entries in batch request"
    )

  def verifyMessageAttributesNumber(n: Int, limits: Limits): Either[String, Unit] = {
    validateWhenLimitAvailable(limits.messageAttributesLimit)(
      n <= _,
      s"Number of message attributes [$n] exceeds the allowed maximum [10]."
    )
  }

  def verifyNumberOfMessagesFromParameters(
      numberOfMessagesFromParameters: Int,
      limits: Limits
  ): Either[String, Unit] =
    validateWhenLimitAvailable(limits.numberOfMessagesLimit)(
      limit => limit.isBetween(numberOfMessagesFromParameters),
      "ReadCountOutOfRange"
    )

  def verifyMessageStringAttribute(
      attributeName: String,
      attributeValue: String,
      limits: Limits
  ): Either[String, Unit] =
    for {
      _ <- validateWhenLimitAvailable(limits.nonEmptyAttributesLimit)(
        shouldValidate => if (shouldValidate) attributeValue.nonEmpty else true,
        s"Attribute '$attributeName' must contain a non-empty value of type 'String'"
      )
      _ <- validateCharactersAndLength(attributeValue, limits)
    } yield ()

  def verifyMessageBody(body: String, limits: Limits): Either[String, Unit] =
    for {
      _ <- validateWhenLimitAvailable(limits.nonEmptyAttributesLimit)(
        shouldValidate => if (shouldValidate) body.nonEmpty else true,
        "The request must contain the parameter MessageBody."
      )
      _ <- validateCharactersAndLength(body, limits)
    } yield ()

  private def validateCharactersAndLength(stringValue: String, limits: Limits): Either[String, Unit] =
    for {
      _ <- validateWhenLimitAvailable(limits.bodyValidCharactersLimit)(
        rangeLimits =>
          stringValue
            .codePoints()
            .iterator
            .asScala
            .forall(codePoint => rangeLimits.exists(range => range.isBetween(codePoint))),
        "InvalidMessageContents"
      )
      _ <- verifyMessageLength(stringValue.length, limits)
    } yield ()

  def verifyMessageNumberAttribute(
      stringNumberValue: String,
      attributeName: String,
      limits: Limits
  ): Either[String, Unit] = {
    for {
      _ <- validateWhenLimitAvailable(limits.nonEmptyAttributesLimit)(
        shouldValidate => if (shouldValidate) stringNumberValue.nonEmpty else true,
        s"Attribute '$attributeName' must contain a non-empty value of type 'Number'"
      )
      _ <- validateWhenLimitAvailable(limits.numberAttributeValueLimit)(
        limit => Try(BigDecimal(stringNumberValue)).toOption.exists(value => limit.isBetween(value)),
        s"Number attribute value $stringNumberValue should be in range (-10**128..10**126)"
      )
    } yield ()
  }

  def verifyMessageWaitTime(messageWaitTime: Long, limits: Limits): Either[String, Unit] = {
    for {
      _ <- if (messageWaitTime < 0) Left("InvalidParameterValue") else Right(())
      _ <- validateWhenLimitAvailable(limits.messageWaitTimeLimit)(
        limit => limit.isBetween(messageWaitTime),
        "InvalidParameterValue"
      )
    } yield ()
  }

  def verifyMessageLength(messageLength: Int, limits: Limits): Either[String, Unit] = {
    validateWhenLimitAvailable(limits.maxMessageLength)(
      limit => messageLength <= limit,
      "MessageTooLong"
    )
  }

  val InvalidQueueNameError = "Can only include alphanumeric characters, hyphens, or underscores."
  val InvalidFifoQueueNameError =
    "The name of a FIFO queue can only include alphanumeric characters, hyphens, or underscores, must end with .fifo suffix."

  def verifyQueueName(queueName: String, isFifo: Boolean, limits: Limits): Either[String, Unit] = {
    for {
      _ <-
        if (isFifo && !queueName.matches("[\\p{Alnum}_-]+\\.fifo"))
          Left(InvalidFifoQueueNameError)
        else Right(())
      _ <-
        if (!isFifo && !queueName.matches("[\\p{Alnum}_-]+"))
          Left(InvalidQueueNameError)
        else Right(())
      _ <- validateWhenLimitAvailable(limits.queueNameLengthLimit)(
        limit => queueName.length <= limit,
        limits.queueNameLengthLimit match {
          case LimitedValue(v) => s"The name of a queue must not be longer than $v."
          case _               => ""
        }
      )
    } yield ()
  }

  private def validateWhenLimitAvailable[LimitValue](
      limit: Limit[LimitValue]
  )(validation: LimitValue => Boolean, error: String): Either[String, Unit] =
    limit match {
      case LimitedValue(value) => if (!validation(value)) Left(error) else Right(())
      case NoLimit             => Right(())
    }
}
