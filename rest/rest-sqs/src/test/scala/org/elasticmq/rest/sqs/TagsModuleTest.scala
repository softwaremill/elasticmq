package org.elasticmq.rest.sqs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TagsModuleTest extends AnyFlatSpec with AttributesModule with Matchers {

  "Key Value pair for tag" should "result in map with one entry " in {
    val parameters = Map("Tag.1.Key" -> "Test", "Tag.1.Value" -> "2")
    val tagsModule = new TagsModule {}
    val tags = tagsModule.tagNameAndValuesReader.read(parameters)
    tags shouldEqual Map("Test" -> "2")
  }

  "Two tags" should "result in two-entry map with correct relation" in {
    val parameters = Map(
      "Tag.1.Key" -> "Test-1", "Tag.1.Value" -> "1",
      "Tag.2.Key" -> "Test-2", "Tag.2.Value" -> "2")
    val tagsModule = new TagsModule {}
    val tags = tagsModule.tagNameAndValuesReader.read(parameters)
    tags shouldEqual Map("Test-1" -> "1", "Test-2" -> "2")
  }

  "Empty value for tag" should "be read as empty string" in {
    val parameters = Map("Tag.1.Key" -> "Test")
    val tagsModule = new TagsModule {}
    val tags = tagsModule.tagNameAndValuesReader.read(parameters)
    tags("Test") shouldEqual ""
  }
}
