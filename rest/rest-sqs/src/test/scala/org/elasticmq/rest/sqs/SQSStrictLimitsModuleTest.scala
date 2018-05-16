package org.elasticmq.rest.sqs

import org.scalatest.FunSuite

import scala.util.Random

class SQSStrictLimitsModuleTest extends FunSuite with SQSLimitsModule {

  override def sqsLimits: SQSLimits.Value = SQSLimits.Strict

  val correctValues =
    List(BigDecimal(10).pow(126), -BigDecimal(10).pow(128), BigDecimal(0), BigDecimal(Random.nextDouble())).map(v =>
      v.toString)
  val incorrectValues =
    List((BigDecimal(10).pow(126) + 0.1).toString, (-BigDecimal(10).pow(128) - 0.1).toString, "12312312a")

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
