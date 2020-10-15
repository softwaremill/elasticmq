package org.elasticmq

import java.math.MathContext

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

class LimitsTest extends AnyWordSpec with Matchers with EitherValues {

  "Validation of batch size limits in strict mode" should {
    "pass if the size of the batch is less than the limit (10)" in {
      Limits.verifyBatchSize(5, StrictSQSLimits) shouldBe Right(())
    }

    "fail if the size of the batch is greater than the limit (10)" in {
      val error = Limits.verifyBatchSize(15, StrictSQSLimits).left.value
      error shouldBe "AWS.SimpleQueueService.TooManyEntriesInBatchRequest"
    }
  }

  "Validation of batch size limits in relaxed mode" should {
    "always pass" in {
      Limits.verifyBatchSize(-5, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyBatchSize(5, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyBatchSize(15, RelaxedSQSLimits) shouldBe Right(())
    }
  }

  "Validation of number of messages from parameters in strict mode" should {
    "pass if the number is between the limits (1-10)" in {
      Limits.verifyNumberOfMessagesFromParameters(1, StrictSQSLimits) shouldBe Right(())
      Limits.verifyNumberOfMessagesFromParameters(5, StrictSQSLimits) shouldBe Right(())
      Limits.verifyNumberOfMessagesFromParameters(10, StrictSQSLimits) shouldBe Right(())
    }

    "fail the validation if the number is less than the lower bound" in {
      val error = Limits.verifyNumberOfMessagesFromParameters(0, StrictSQSLimits).left.value
      error shouldBe "ReadCountOutOfRange"
    }

    "fail the validation if the number is greater than the upper bound" in {
      val error = Limits.verifyNumberOfMessagesFromParameters(15, StrictSQSLimits).left.value
      error shouldBe "ReadCountOutOfRange"
    }
  }

  "Validation of number of messages from parameters in relaxed mode" should {
    "always pass the validation" in {
      Limits.verifyNumberOfMessagesFromParameters(-5, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyNumberOfMessagesFromParameters(0, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyNumberOfMessagesFromParameters(5, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyNumberOfMessagesFromParameters(15, RelaxedSQSLimits) shouldBe Right(())
    }
  }

  "Validation of message string attribute in strict mode" should {
    "pass if string attribute contains only allowed characters" in {
      val testString = List(0x9, 0xa, 0xd, 0x21, 0xe005, 0x10efff).map(_.toChar).mkString
      Limits.verifyMessageStringAttribute(testString, StrictSQSLimits) shouldBe Right(())
    }

    "pass if the string is empty" in {
      Limits.verifyMessageStringAttribute("", StrictSQSLimits) shouldBe Right(())
    }

    "fail if string contains any not allowed character" in {
      val testString = List(0x9, 0xa, 0xd, 0x21, 0xe005, 0x19, 0x10efff).map(_.toChar).mkString
      val error = Limits.verifyMessageStringAttribute(testString, StrictSQSLimits).left.value
      error shouldBe "InvalidMessageContents"
    }
  }

  "Validation of message string attribute in relaxed mode" should {
    "pass if string attribute contains only allowed characters" in {
      val testString = List(0x9, 0xa, 0xd, 0x21, 0xe005, 0x10efff).map(_.toChar).mkString
      Limits.verifyMessageStringAttribute(testString, RelaxedSQSLimits) shouldBe Right(())
    }

    "pass if the string is empty" in {
      Limits.verifyMessageStringAttribute("", RelaxedSQSLimits) shouldBe Right(())
    }

    "pass if string contains any not allowed character" in {
      val testString = List(0x9, 0xa, 0xd, 0x21, 0xe005, 0x19, 0x10efff).map(_.toChar).mkString
      Limits.verifyMessageStringAttribute(testString, RelaxedSQSLimits) shouldBe Right(())
    }
  }

  "Validation of message number attribute in strict mode" should {
    "pass if the number is between the limits (-10^128 - 10^126)" in {
      Limits.verifyMessageNumberAttribute(BigDecimal(10).pow(126).toString(), StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute((-BigDecimal(10).pow(128)).toString(), StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute(BigDecimal(0).toString(), StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute(
        BigDecimal(Random.nextDouble()).toString(),
        StrictSQSLimits
      ) shouldBe Right(
        ()
      )
    }

    "fail if the number is bigger than the upper bound" in {
      val overUpperBound = BigDecimal(10, MathContext.UNLIMITED).pow(126) + BigDecimal(0.1)
      val error = Limits.verifyMessageNumberAttribute(overUpperBound.toString, StrictSQSLimits)
      error shouldBe Left(s"Number attribute value $overUpperBound should be in range (-10**128..10**126)")
    }

    "fail if the number is below the lower bound" in {
      val belowLowerBound = -BigDecimal(10, MathContext.UNLIMITED).pow(128) - BigDecimal(0.1)
      val error =
        Limits.verifyMessageNumberAttribute(belowLowerBound.toString, StrictSQSLimits)
      error shouldBe Left(s"Number attribute value $belowLowerBound should be in range (-10**128..10**126)")
    }

    "fail if the number can't be parsed" in {
      val error = Limits.verifyMessageNumberAttribute("12312312a", StrictSQSLimits).left.value
      error shouldBe s"Number attribute value 12312312a should be in range (-10**128..10**126)"
    }
  }

  "Validation of message number attribute in relaxed mode" should {
    "always pass the validation" in {
      val belowLowerBound = -BigDecimal(10).pow(128) - BigDecimal(0.1)
      val overUpperBound = BigDecimal(10).pow(126) + BigDecimal(0.1)
      Limits.verifyMessageNumberAttribute(belowLowerBound.toString, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute(BigDecimal(10).pow(126).toString(), RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute((-BigDecimal(10).pow(128)).toString(), RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute(overUpperBound.toString, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageNumberAttribute("12312312a", RelaxedSQSLimits) shouldBe Right(())
    }
  }

  "Validation of message wait time in strict mode" should {
    "pass if the wait time is between the limit range (0-20)" in {
      Limits.verifyMessageWaitTime(0, StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageWaitTime(13, StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageWaitTime(20, StrictSQSLimits) shouldBe Right(())
    }

    "fail if the number is below the lower bound" in {
      val error = Limits.verifyMessageWaitTime(-1, StrictSQSLimits).left.value
      error shouldBe "InvalidParameterValue"
    }

    "fail if the number is above the upper bound" in {
      val error = Limits.verifyMessageWaitTime(21, StrictSQSLimits).left.value
      error shouldBe "InvalidParameterValue"
    }
  }

  "Validation of message wait time in relaxed mode" should {
    "pass if the wait time is bigger than 0" in {
      Limits.verifyMessageWaitTime(0, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageWaitTime(13, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageWaitTime(20, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageWaitTime(25, RelaxedSQSLimits) shouldBe Right(())
    }

    "fail if the wait time is lower than 0" in {
      val error = Limits.verifyMessageWaitTime(-1, StrictSQSLimits).left.value
      error shouldBe "InvalidParameterValue"
    }
  }

  "Validation of message length in strict mode" should {
    "pass if the length is smaller than the limit (262144)" in {
      Limits.verifyMessageLength(-5, StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(0, StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(100, StrictSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(262144, StrictSQSLimits) shouldBe Right(())
    }

    "fail if the length is bigger than the limit" in {
      val error = Limits.verifyMessageLength(300000, StrictSQSLimits).left.value
      error shouldBe "MessageTooLong"
    }
  }

  "Validation of message length in relaxed mode" should {
    "always pass" in {
      Limits.verifyMessageLength(-5, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(0, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(100, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(262143, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(262144, RelaxedSQSLimits) shouldBe Right(())
      Limits.verifyMessageLength(300000, RelaxedSQSLimits) shouldBe Right(())
    }
  }

  "Validation of queue name in strict mode" should {
    "pass if queue name is made of alphanumeric characters and has length smaller than 80" in {
      Limits.verifyQueueName("abc123.-_", isFifo = false, StrictSQSLimits) shouldBe Right(())
    }

    "fail if queue name contains invalid characters" in {
      val error = Limits.verifyQueueName("invalid#characters&.fifo", isFifo = true, StrictSQSLimits).left.value
      error shouldBe "InvalidParameterValue"
    }

    "fail if normal queue name exceeds 80 characters limit cap" in {
      val error = Limits
        .verifyQueueName(
          "over80CharactersOver80CharactersOver80CharactersOver80CharactersOver80Characterss",
          isFifo = false,
          StrictSQSLimits
        )
        .left
        .value
      error shouldBe "InvalidParameterValue"
    }
  }

  "Validation of queue name in relaxed mode" should {
    "pass when queue name is made of alphanumeric characters" in {
      Limits.verifyQueueName("abc123.-_", isFifo = false, RelaxedSQSLimits) shouldBe Right(())
    }

    "pass when normal queue name exceeds 80 characters limit cap" in {
      Limits.verifyQueueName(
        "over80CharactersOver80CharactersOver80CharactersOver80CharactersOver80Characterss",
        isFifo = false,
        RelaxedSQSLimits
      ) shouldBe Right(())
    }

    "fail if queue name contains invalid characters" in {
      val error =
        Limits.verifyQueueName("invalid#characters&.fifo", isFifo = true, RelaxedSQSLimits).left.value
      error shouldBe "InvalidParameterValue"
    }
  }
}
