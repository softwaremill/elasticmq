package org.elasticmq

import scala.collection.JavaConverters._
import scala.util.Try

trait Limits {
  def queueNameLengthLimit: Limit[Int]
  def batchSizeLimit: Limit[Int]
  def numberOfMessagesLimit: Limit[RangeLimit[Int]]
  def bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]]
  def numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]]
  def messageWaitTimeLimit: Limit[RangeLimit[Long]]
  def maxMessageLength: Limit[Int]
}

case object StrictSQSLimits extends Limits {
  override val queueNameLengthLimit: Limit[Int] = LimitedValue(80)
  override val batchSizeLimit: Limit[Int] = LimitedValue(10)
  override val numberOfMessagesLimit: Limit[RangeLimit[Int]] = LimitedValue(RangeLimit(1, 10))
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
  override val maxMessageLength: Limit[Int] = LimitedValue(262144)
}
case object RelaxedSQSLimits extends Limits {
  override val queueNameLengthLimit: Limit[Int] = NoLimit
  override val batchSizeLimit: Limit[Int] = NoLimit
  override val numberOfMessagesLimit: Limit[RangeLimit[Int]] = NoLimit
  override val bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]] = NoLimit
  override val numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]] = NoLimit
  override val messageWaitTimeLimit: Limit[RangeLimit[Long]] = NoLimit
  override val maxMessageLength: Limit[Int] = NoLimit
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
      "AWS.SimpleQueueService.TooManyEntriesInBatchRequest"
    )

  def verifyNumberOfMessagesFromParameters(
      numberOfMessagesFromParameters: Int,
      limits: Limits
  ): Either[String, Unit] =
    validateWhenLimitAvailable(limits.numberOfMessagesLimit)(
      limit => limit.isBetween(numberOfMessagesFromParameters),
      "ReadCountOutOfRange"
    )

  def verifyMessageStringAttribute(stringAttribute: String, limits: Limits): Either[String, Unit] =
    for {
      _ <- validateWhenLimitAvailable(limits.bodyValidCharactersLimit)(
        rangeLimits =>
          stringAttribute
            .codePoints()
            .iterator
            .asScala
            .forall(codePoint => rangeLimits.exists(range => range.isBetween(codePoint))),
        "InvalidMessageContents"
      )
      _ <- verifyMessageLength(stringAttribute.length, limits)
    } yield ()

  def verifyMessageNumberAttribute(stringNumberValue: String, limits: Limits): Either[String, Unit] = {
    validateWhenLimitAvailable(limits.numberAttributeValueLimit)(
      limit => Try(BigDecimal(stringNumberValue)).toOption.exists(value => limit.isBetween(value)),
      s"Number attribute value $stringNumberValue should be in range (-10**128..10**126)"
    )
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

  def verifyQueueName(queueName: String, isFifo: Boolean, limits: Limits): Either[String, Unit] = {
    for {
      _ <- if (!queueName.matches("[\\p{Alnum}\\._-]*")) Left("InvalidParameterValue") else Right(())
      _ <- if (isFifo && !queueName.endsWith(".fifo")) Left("InvalidParameterValue") else Right(())
      _ <- validateWhenLimitAvailable(limits.queueNameLengthLimit)(
        limit => queueName.length <= limit,
        "InvalidParameterValue"
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
