package org.elasticmq.rest.sqs

trait AttributeNamesReaderModule {
  val attributeNamesReader = new AttributeNamesReader

  class AttributeNamesReader {
    def read(parameters: Map[String, String], allAttributeNames: List[String]) = {
      def collect(suffix: Int, acc: List[String]): List[String] = {
        parameters.get("AttributeName." + suffix) match {
          case None => acc
          case Some(an) => collect(suffix+1, an :: acc)
        }
      }

      def unfoldAllAttributeIfRequested(attributeNames: List[String]): List[String] = {
        if (attributeNames.contains("All")) {
          allAttributeNames
        } else {
          attributeNames
        }
      }

      val rawAttributeNames = collect(1, parameters.get("AttributeName").toList)
      val attributeNames = unfoldAllAttributeIfRequested(rawAttributeNames)

      attributeNames
    }
  }
}