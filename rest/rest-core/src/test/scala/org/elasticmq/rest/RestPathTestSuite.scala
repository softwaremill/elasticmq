package org.elasticmq.rest

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import org.scalatest.prop.TableDrivenPropertyChecks

class RestPathTestSuite extends FunSuite with MustMatchers with TableDrivenPropertyChecks {
  import RestPath._

  val paths = Table[RestPath, String, Option[Map[String, String]]](
    ("rest path", "input path", "expected result"),
    (root / "a" / "b", "/a/b", Some(Map())),
    (root / "a" / "b", "/a/c", None),
    (root / "a" / "b", "/a", None),
    (root / "a" / "b", "/a/b/d", None),
    (root / "a" / """[a-z][0-9]+""".r, "/a/aa", None),
    (root / "a" / """[a-z][0-9]+""".r, "/a/a123", Some(Map())),
    (root / "a" / %("p1") / "c", "/a/zzz/c", Some(Map("p1" -> "zzz"))),
    (root / "a" / %("p1") / %("p2"), "/a/zzz/yyy", Some(Map("p1" -> "zzz", "p2" -> "yyy"))),
    (root / "a" / %("p1") / %("p2"), "/a/zzz", None)
  )

  forAll (paths) { (restPath, inputPath, expectedResult) =>
    test(restPath+" for "+inputPath+" should give result "+expectedResult) {
      restPath.matchAndExtract(inputPath) must equal (expectedResult)
    }
  }
}