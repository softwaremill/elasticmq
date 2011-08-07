package org.elasticmq.rest

import util.matching.Regex

abstract class RestPath {
  val next: Option[RestPath]
  def matchAndExtract(path: String): Option[Map[String, String]]

  protected def nextMatchAndExtract(rest: Option[String]): Option[Map[String, String]] = {
    (next, rest) match {
      case (Some(rp), Some(path)) => rp.matchAndExtract(path)
      case (None, None) => Some(Map())
      case _ => None
    }
  }

  def nextComponent(path: String) = {
    val splitResult = path.split("/", 2)
    val component = splitResult(0)
    val rest = if (splitResult.length > 1 && splitResult(1).length > 0) Some(splitResult(1)) else None

    (component, rest)
  }

  def copy(newNext: Option[RestPath]): RestPath

  def singleToString: String
  override def toString = singleToString+(next match { case Some(rp) => "/"+rp.toString case None => "" })
}

case class StringRestPath(part: String, next: Option[RestPath]) extends RestPath {
  def matchAndExtract(path: String) = {
    val (component, rest) = nextComponent(path)
    if (component == part) nextMatchAndExtract(rest) else None
  }

  def copy(newNext: Option[RestPath]) = StringRestPath(part, newNext)

  override def singleToString = part
}

case class RegexRestPath(regex: Regex, next: Option[RestPath]) extends RestPath {
  def matchAndExtract(path: String) = {
    val (component, rest) = nextComponent(path)
    if (regex.pattern.matcher(component).matches) nextMatchAndExtract(rest) else None
  }

  def copy(newNext: Option[RestPath]) = RegexRestPath(regex, newNext)

  override def singleToString = regex.toString()
}

case class ParamRestPath(param: RestPathParam, next: Option[RestPath]) extends RestPath {
  def matchAndExtract(path: String) = {
    val (component, rest) = nextComponent(path)
    nextMatchAndExtract(rest).map(_ + (param.name -> component))
  }

  def copy(newNext: Option[RestPath]) = ParamRestPath(param, newNext)

  override def singleToString = "%"+param.name
}

class RestPathCreator(val previous: List[RestPath]) {
  def /(part: String) = new RestPathCreator(new StringRestPath(part, None) :: previous)
  def /(regex: Regex) = new RestPathCreator(new RegexRestPath(regex, None) :: previous)
  def /(param: RestPathParam) = new RestPathCreator(new ParamRestPath(param, None) :: previous)

  def build(pathList: List[RestPath], next: Option[RestPath]): Option[RestPath] = {
    pathList match {
      case Nil => next
      case path :: pathListTail => build(pathListTail, Some(path.copy(next)))
    }
  }

  def build(): RestPath = build(previous, None) match {
    // Special case: root path
    case None => StringRestPath("", None)
    case Some(rp) => StringRestPath("", Some(rp))
  }
}

case class RestPathParam(name: String)

/**
 * Usage example:
 * <pre>
 * import RestPath._
 * root / "a" / "b" / "c"
 * root / "a" / """[a-zA-Z]+[0-9]""".r / "c"
 * root / "a" / $("p1") / "c"
 * </pre>
 */
object RestPath {
  def root = new RestPathCreator(Nil)

  def %(name: String) = RestPathParam(name)

  implicit def restPathCreatorToRestPath(rpc: RestPathCreator): RestPath = rpc.build()
}