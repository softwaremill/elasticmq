package org.elasticmq.rest.sqs

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AttributesModuleTest extends AnyWordSpec with AttributesModule with Matchers {

  "PossiblyEmptyAttributeValuesCalculator" should {

    "Return list containing values from rules that returned some value" in {
      val rule1 = AttributeValuesCalculator.Rule("rule1", () => Option.empty[String])
      val rule2 = AttributeValuesCalculator.Rule("rule2", () => Option("value2"))
      val rule3 = AttributeValuesCalculator.Rule("rule3", () => Option.empty[String])
      val rule4 = AttributeValuesCalculator.Rule("rule4", () => Option("value4"))

      val result = new PossiblyEmptyAttributeValuesCalculator().calculate(
        List("rule1", "rule2", "rule3", "rule4"),
        rule1,
        rule2,
        rule3,
        rule4
      )

      result shouldBe List(
        ("rule2", "value2"),
        ("rule4", "value4")
      )
    }

    "Return empty list if all rules does not return values" in {
      val rule1 = AttributeValuesCalculator.Rule("rule1", () => Option.empty[String])
      val rule2 = AttributeValuesCalculator.Rule("rule2", () => Option.empty[String])
      val rule3 = AttributeValuesCalculator.Rule("rule3", () => Option.empty[String])
      val rule4 = AttributeValuesCalculator.Rule("rule4", () => Option.empty[String])

      val result = new PossiblyEmptyAttributeValuesCalculator().calculate(
        List("rule1", "rule2", "rule3", "rule4"),
        rule1,
        rule2,
        rule3,
        rule4
      )

      result shouldBe List()
    }

    "Return values only for mentioned rules" in {
      val rule1 = AttributeValuesCalculator.Rule("rule1", () => Option.empty[String])
      val rule2 = AttributeValuesCalculator.Rule("rule2", () => Option("value2"))
      val rule3 = AttributeValuesCalculator.Rule("rule3", () => Option.empty[String])
      val rule4 = AttributeValuesCalculator.Rule("rule4", () => Option("value4"))

      val result = new PossiblyEmptyAttributeValuesCalculator().calculate(
        List("rule1", "rule4"),
        rule1,
        rule2,
        rule3,
        rule4
      )

      result shouldBe List(
        ("rule4", "value4")
      )
    }
  }

}
