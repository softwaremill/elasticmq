package org.elasticmq.rest.sqs

import scala.util.Random
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BatchRequestsModuleTest extends AnyFunSuite with Matchers {
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

  test("should preserve the order for sub parameters") {
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
    subParameters should contain theSameElementsInOrderAs List(
      Map("Key1" -> "Value1", "Key2" -> "Value2", "Key3" -> "Value3"),
      Map("Key21" -> "Value21", "Key22" -> "Value22"),
      Map("Key41" -> "Value41", "Multi.Key.1" -> "ValueMulti")
    )
  }

  test("should preserve the order for sub parameters with a size greater than 10") {
    // Given
    val prefix = "SomePrefix"

    // SomePrefix.1.Key1 -> Value1-1
    // SomePrefix.1.Key2 -> Value1-2
    // SomePrefix.1.Key3 -> Value1-3
    // SomePrefix.1.Key4 -> Value1-4
    // ... not sorted to SomePrefix.20.Key4 -> Value20-4
    val parameters = Random
      .shuffle(for {
        i <- 1 to 20
        j <- 1 to 4
      } yield s"$prefix.$i.Key$j" -> s"Value$i-$j")
      .toMap

    // When
    val subParameters = BatchRequestsModule.subParametersMaps(prefix, parameters)

    // Then
    // Key4 -> Value1-4
    // Key1 -> Value1-1
    // Key3 -> Value1-3
    // Key2 -> Value1-2
    // sorted by discriminator, whereas the values for a discriminator are not sorted
    for (i <- 1 to 20) {
      subParameters(i - 1) should contain theSameElementsAs (1 to 4).map { j =>
        s"Key$j" -> s"Value$i-$j"
      }
    }
  }
}
