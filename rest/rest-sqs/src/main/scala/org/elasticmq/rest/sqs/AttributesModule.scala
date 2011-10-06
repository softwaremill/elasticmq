package org.elasticmq.rest.sqs

trait AttributesModule {
  val attributeNamesReader = new AttributeNamesReader
  val attributesToXmlConverter = new AttributesToXmlConverter

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

  class AttributesToXmlConverter {
    def convert(attributes: List[(String, String)]) = {
      attributes.map(a =>
        <Attribute>
          <Name>{a._1}</Name>
          <Value>{a._2}</Value>
        </Attribute>)
    }
  }
}