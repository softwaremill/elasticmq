package org.elasticmq.rest.sqs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TagsModuleTest extends AnyFlatSpec with AttributesModule with Matchers {

  "Empty value for tag" should "be read as empty string" in {
    val parameters = Map("Tag.1.Key" -> "Test")
    val tags = TagsModule.tagNameAndValuesReader.read(parameters)
    tags("Test") shouldEqual  ""
  }
}
