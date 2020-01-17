package org.elasticmq.rest.sqs

import scala.util.Random
import org.scalatest.funsuite.AnyFunSuite

class SQSStrictLimitsModuleTest extends AnyFunSuite with SQSLimitsModule {

  override def sqsLimits: SQSLimits.Value = SQSLimits.Strict

  val correctValues =
    List(BigDecimal(10).pow(126), -BigDecimal(10).pow(128), BigDecimal(0), BigDecimal(Random.nextDouble()))
      .map(v => v.toString)
  val incorrectValues =
    List(
      new java.math.BigDecimal(10).pow(126).add(new java.math.BigDecimal(0.1)).toString,
      new java.math.BigDecimal(10).pow(128).subtract(new java.math.BigDecimal(0.1)).toString,
      "12312312a"
    )

  for (value <- correctValues) {
    test(s"should verify message number attribute value $value") {
      verifyMessageNumberAttribute(value)
    }
  }

  for (value <- incorrectValues) {
    test(s"should throw an exception if number attribute value is out of range or NaN $value") {
      val thrown = intercept[SQSException] {
        verifyMessageNumberAttribute(value)
      }
      assert(thrown.message.startsWith(s"Number attribute value $value should be in range (-10**128..10**126)"))
    }
  }

}
