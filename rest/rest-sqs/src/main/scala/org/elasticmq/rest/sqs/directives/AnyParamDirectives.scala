package org.elasticmq.rest.sqs.directives

import shapeless._
import spray.routing._
import spray.routing.directives._
import spray.http.HttpForm
import spray.httpx.unmarshalling._
import spray.routing.directives.NameReceptacle
import shapeless.::

trait AnyParamDirectives {
  import BasicDirectives._
  import RouteDirectives._

  def anyParam(apdm: AnyParamDefMagnet): apdm.Out = apdm()

  def anyParamsMap: Directive[Map[String, String] :: HNil] = {
    BasicDirectives.extract { ctx =>
      val queryParams = ctx.request.uri.query.toMap
      ctx.request.entity.as[HttpForm].right.map((_, queryParams))
    }.flatMap {
      case Right((httpForm, queryParams)) => {
        val fieldNames = httpForm.fields.keySet

        val rejectionsOrPairs = for (fieldName <- fieldNames) yield {
          httpForm.field(fieldName).as[String] match {
            case Left(deserializationError) => Left(toRejection(deserializationError, fieldName))
            case Right(fieldValue) => Right(fieldName -> fieldValue)
          }
        }

        rejectionsOrPairs.collectFirst {
          case Left(x) => x
        } match {
          case Some(x) => reject(x)
          case None => provide(queryParams ++ rejectionsOrPairs.collect { case Right(y) => y } )
        }
      }

      case Left(deserializationError) => reject(toRejection(deserializationError, "?"))
    }
  }

  // From FormFieldDirectives
  private def toRejection(deserializationError: DeserializationError, fieldName: String) = deserializationError match {
    case ContentExpected => MissingFormFieldRejection(fieldName)
    case MalformedContent(msg, _) => MalformedFormFieldRejection(msg, fieldName)
    case UnsupportedContentType(msg) => UnsupportedRequestContentTypeRejection(msg)
  }
}

object AnyParamDirectives extends AnyParamDirectives

trait AnyParamDefMagnet {
  type Out
  def apply(): Out
}

object AnyParamDefMagnet {
  implicit def forString(value: String)(implicit apdm2: AnyParamDefMagnet2[Tuple1[String]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forNR[T](value: NameReceptacle[T])(implicit apdm2: AnyParamDefMagnet2[Tuple1[NameReceptacle[T]]]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(Tuple1(value))
    }
  }

  implicit def forTuple[T <: Product](value: T)(implicit apdm2: AnyParamDefMagnet2[T]) = {
    new AnyParamDefMagnet {
      type Out = apdm2.Out
      def apply() = apdm2(value)
    }
  }
}

trait AnyParamDefMagnet2[T] {
  type Out
  def apply(value: T): Out
}

object AnyParamDefMagnet2 {
  implicit def forTuple[T <: Product, L <: HList, Out](implicit
                                                       hla: HListerAux[T, L],
                                                       apdma: AnyParamDefMagnetAux[L]) = {
    new AnyParamDefMagnet2[T] {
      def apply(value: T) = apdma(hla(value))
      type Out = apdma.Out
    }
  }
}

trait AnyParamDefMagnetAux[L] {
  type Out
  def apply(value: L): Out
}

object AnyParamDefMagnetAux {
  implicit def forHList[L <: HList](implicit f: LeftFolder[L, Directive0, MapReduce.type]) = {
    new AnyParamDefMagnetAux[L] {
      type Out = f.Out
      def apply(value: L) = {
        value.foldLeft(BasicDirectives.noop)(MapReduce)
      }
    }
  }

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList]
    (implicit
     fdma: FieldDefMagnetAux[T, Directive[LB]],
     pdma: ParamDefMagnetAux[T, Directive[LB]],
     ev: PrependAux[LA, LB, Out]) = {

      // see https://groups.google.com/forum/?fromgroups=#!topic/spray-user/HGEEdVajpUw
      def fdmaWrapper(t: T): Directive[LB] = fdma(t).hflatMap {
        case None :: HNil => pdma(t)
        case x => BasicDirectives.hprovide(x)
      }

      at[Directive[LA], T] { (a, t) => a & (fdmaWrapper(t) | pdma(t)) }
    }
  }
}
