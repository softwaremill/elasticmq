package org.elasticmq.rest.sqs

import org.scalatest.FunSuite
import org.scalatest.Matchers

class BatchRequestsModuleTest extends FunSuite with Matchers {
  test("should correctly find sub parameters") {
    // Given
    val parameters = Map(
      "WihtoutPrefix.1.Key" -> "Value",
      "SomePrefix.1.Key1" -> "Value1",
      "SomePrefix.1.Key2" -> "Value2",
      "SomePrefix.1.Key3" -> "Value3",
      "SomePrefix.2.Key21" -> "Value21",
      "SomePrefixAndMore.1.Key" -> "Value",
      "SomePrefix.2.Key22" -> "Value22",
      "SomePrefix.4.Key41" -> "Value41",
      "SomePrefix.4.Multi.Key.1" -> "ValueMulti"
    )

    // When
    val subParameters = BatchRequestsModule.subParametersMaps("SomePrefix", parameters)

    // Then
    subParameters should have length (3)
    subParameters should contain(Map("Key1" -> "Value1", "Key2" -> "Value2", "Key3" -> "Value3"))
    subParameters should contain(Map("Key21" -> "Value21", "Key22" -> "Value22"))
    subParameters should contain(Map("Key41" -> "Value41", "Multi.Key.1" -> "ValueMulti"))
  }
}
