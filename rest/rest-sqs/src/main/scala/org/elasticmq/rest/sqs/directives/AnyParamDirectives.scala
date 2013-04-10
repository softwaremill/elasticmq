package org.elasticmq.rest.sqs.directives

import shapeless._
import spray.routing._
import spray.routing.directives._

trait AnyParamDirectives {
  def anyParam(apdm: AnyParamDefMagnet): apdm.Out = apdm()
}

object AnyParamDirectives extends AnyParamDirectives

trait AnyParamDefMagnet {
  type Out
  def apply(): Out
}

object AnyParamDefMagnet {
  import FormFieldDirectives._
  import ParameterDirectives._

  implicit def forString(value: String) = {
    val route = formFields(value) | parameters(value)

    new AnyParamDefMagnet {
      type Out = route.type
      def apply() = route
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
