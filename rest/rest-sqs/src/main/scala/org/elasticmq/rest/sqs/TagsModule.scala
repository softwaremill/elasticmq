package org.elasticmq.rest.sqs

trait TagsModule {
  val tagNameAndValuesReader = new TagNameAndValuesReader
  val tagNamesReader = new TagNamesReader
  val tagsToXmlConverter = new TagsToXmlConverter

  class TagNameAndValuesReader {
    def read(parameters: Map[String, String]): Map[String, String] = {
      // Fix for buggy Java library that sends "Tags" instead of "Tag" as per API specification
      var tagPrefix = "Tag."
      if (parameters.keySet.contains("Tags.1.Key")) {
        tagPrefix = "Tags."
      }
      def collect(suffix: Int, acc: Map[String, String]): Map[String, String] = {
        parameters.get(tagPrefix + suffix + ".Key") match {
          case None => acc
          case Some(an) =>
            parameters.get(tagPrefix + suffix + ".Value") match {
              case None => collect(suffix + 1, acc + (an -> ""))
              case Some(tagValue) => collect(suffix + 1, acc + (an -> tagValue))
            }
        }
      }

      collect(1, Map())
    }
  }

  class TagNamesReader {
    def read(parameters: Map[String, String]): List[String] = {
      def collect(suffix: Int, acc: List[String]): List[String] = {
        parameters.get("TagKey." + suffix) match {
          case None     => acc
          case Some(an) => collect(suffix + 1, an :: acc)
        }
      }

      collect(1, parameters.get("TagKey").toList)
    }
  }

  class TagsToXmlConverter {
    def convert(tags: Map[String, String]) = {
      tags.map(t => <Tag>
        <Key>{t._1}</Key>
        <Value>{t._2}</Value>
      </Tag>)
    }
  }
}