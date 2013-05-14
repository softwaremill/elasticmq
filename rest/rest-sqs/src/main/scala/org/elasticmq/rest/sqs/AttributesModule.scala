package org.elasticmq.rest.sqs

trait AttributesModule {
  val attributeNamesReader = new AttributeNamesReader
  val attributesToXmlConverter = new AttributesToXmlConverter
  val attributeValuesCalculator = new AttributeValuesCalculator
  val attributeNameAndValuesReader = new AttributeNameAndValuesReader

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

  class AttributeValuesCalculator {
    import AttributeValuesCalculator.Rule

    def calculate[T](attributeNames: List[String], rules: Rule[T]*): List[(String, T)] = {
      attributeNames.flatMap(attribute => {
        rules.find(rule => rule.attributeName == attribute).map(rule => (rule.attributeName, rule.calculateValue()))
      })
    }
  }
  
  object AttributeValuesCalculator {
    case class Rule[T](attributeName: String, calculateValue: () => T)
  }
  
  class AttributeNameAndValuesReader {
    def read(parameters: Map[String, String]): Map[String, String] = {
      def collect(suffix: Int, acc: Map[String, String]): Map[String, String] = {
        parameters.get("Attribute."+suffix+".Name") match {
          case None => acc
          case Some(an) => collect(suffix+1, acc + (an -> parameters("Attribute."+suffix+".Value")))
        }
      }
      
      collect(1, Map())
    }
  }
}