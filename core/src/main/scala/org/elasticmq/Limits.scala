package org.elasticmq

import java.nio.charset.StandardCharsets
import java.time.Duration
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

  /** The maximum size of a message (body and attributes, in bytes) on a queue that doesn't set `MaximumMessageSize`,
    * and the maximum total size of a `SendMessageBatch` request.
    */
  def maxMessageSize: Limit[Int]

  /** The allowed values of a queue's `MaximumMessageSize` attribute, in bytes. */
  def maximumMessageSizeLimit: Limit[RangeLimit[Int]]

  /** The retention period, in seconds, of messages on a queue that doesn't set `MessageRetentionPeriod`. Without a
    * limit, such messages never expire.
    */
  def defaultMessageRetentionPeriod: Limit[Long]

  /** The allowed values of a queue's `MessageRetentionPeriod` attribute, in seconds. */
  def messageRetentionPeriodLimit: Limit[RangeLimit[Long]]

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
  override val maxMessageSize: Limit[Int] = LimitedValue(1024 * 1024)
  override val maximumMessageSizeLimit: Limit[RangeLimit[Int]] = LimitedValue(RangeLimit(1024, 1024 * 1024))
  override val defaultMessageRetentionPeriod: Limit[Long] = LimitedValue(4L * 24 * 60 * 60)
  override val messageRetentionPeriodLimit: Limit[RangeLimit[Long]] = LimitedValue(RangeLimit(60L, 14L * 24 * 60 * 60))
}
case object RelaxedSQSLimits extends Limits {
  override val queueNameLengthLimit: Limit[Int] = NoLimit
  override val batchSizeLimit: Limit[Int] = NoLimit
  override val numberOfMessagesLimit: Limit[RangeLimit[Int]] = NoLimit
  override val nonEmptyAttributesLimit: Limit[Boolean] = NoLimit
  override val bodyValidCharactersLimit: Limit[List[RangeLimit[Int]]] = NoLimit
  override val numberAttributeValueLimit: Limit[RangeLimit[BigDecimal]] = NoLimit
  override val messageWaitTimeLimit: Limit[RangeLimit[Long]] = NoLimit
  override val maxMessageSize: Limit[Int] = NoLimit
  override val maximumMessageSizeLimit: Limit[RangeLimit[Int]] = NoLimit
  override val defaultMessageRetentionPeriod: Limit[Long] = NoLimit
  override val messageRetentionPeriodLimit: Limit[RangeLimit[Long]] = NoLimit
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
      _ <- validateCharacters(attributeValue, limits)
    } yield ()

  def verifyMessageBody(body: String, limits: Limits): Either[String, Unit] =
    for {
      _ <- validateWhenLimitAvailable(limits.nonEmptyAttributesLimit)(
        shouldValidate => if (shouldValidate) body.nonEmpty else true,
        "The request must contain the parameter MessageBody."
      )
      _ <- validateCharacters(body, limits)
    } yield ()

  private def validateCharacters(stringValue: String, limits: Limits): Either[String, Unit] =
    validateWhenLimitAvailable(limits.bodyValidCharactersLimit)(
      rangeLimits =>
        stringValue
          .codePoints()
          .iterator
          .asScala
          .forall(codePoint => rangeLimits.exists(range => range.isBetween(codePoint))),
      "InvalidMessageContents"
    )

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

  /** The size of a message as SQS counts it: the body, plus the name, data type and value of each attribute, in bytes.
    */
  def messageSize(body: String, messageAttributes: Map[String, MessageAttribute]): Long = {
    def utf8Size(s: String): Long = s.getBytes(StandardCharsets.UTF_8).length.toLong
    val attributesSize = messageAttributes.iterator.map { case (name, attribute) =>
      val valueSize = attribute match {
        case StringMessageAttribute(value, _) => utf8Size(value)
        case NumberMessageAttribute(value, _) => utf8Size(value)
        case BinaryMessageAttribute(value, _) => value.length.toLong
      }
      utf8Size(name) + utf8Size(attribute.getDataType()) + valueSize
    }.sum
    utf8Size(body) + attributesSize
  }

  /** The maximum size of a message on the given queue: its `MaximumMessageSize` if set, otherwise
    * [[Limits.maxMessageSize]].
    */
  def maxMessageSize(queueData: QueueData, limits: Limits): Limit[Int] =
    queueData.maximumMessageSize.map(LimitedValue(_)).getOrElse(limits.maxMessageSize)

  def verifyMessageSize(messageSize: Long, maxMessageSize: Limit[Int]): Either[String, Unit] =
    validateWhenLimitAvailable(maxMessageSize)(
      limit => messageSize <= limit,
      maxMessageSize match {
        case LimitedValue(v) => s"One or more parameters are invalid. Reason: Message must be shorter than $v bytes."
        case NoLimit         => ""
      }
    )

  /** The retention period of messages on the given queue: its `MessageRetentionPeriod` if set, otherwise
    * [[Limits.defaultMessageRetentionPeriod]]. `None` means messages never expire.
    */
  def messageRetentionPeriod(queueData: QueueData, limits: Limits): Option[Duration] =
    queueData.messageRetentionPeriod.orElse(limits.defaultMessageRetentionPeriod match {
      case LimitedValue(seconds) => Some(Duration.ofSeconds(seconds))
      case NoLimit               => None
    })

  def verifyMessageRetentionPeriod(seconds: Long, limits: Limits): Either[String, Unit] =
    for {
      _ <- if (seconds <= 0) Left(invalidMessageRetentionPeriod(seconds)) else Right(())
      _ <- validateWhenLimitAvailable(limits.messageRetentionPeriodLimit)(
        _.isBetween(seconds),
        invalidMessageRetentionPeriod(seconds)
      )
    } yield ()

  private def invalidMessageRetentionPeriod(seconds: Long) =
    s"Invalid value for the parameter MessageRetentionPeriod: $seconds."

  def verifyMaximumMessageSize(bytes: Long, limits: Limits): Either[String, Unit] =
    for {
      _ <- if (bytes <= 0 || bytes > Int.MaxValue) Left(invalidMaximumMessageSize(bytes)) else Right(())
      _ <- validateWhenLimitAvailable(limits.maximumMessageSizeLimit)(
        _.isBetween(bytes.toInt),
        invalidMaximumMessageSize(bytes)
      )
    } yield ()

  private def invalidMaximumMessageSize(bytes: Long) =
    s"Invalid value for the parameter MaximumMessageSize: $bytes."

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
