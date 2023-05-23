package org.elasticmq.rest.sqs

import Constants.QueueUrlParameter

import java.util.regex.Pattern
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, RootJsonFormat}

import scala.concurrent.Future

trait BatchRequestsModule {
  this: SQSLimitsModule with ActorSystemModule =>

  def batchRequest[M <: BatchEntry, R](messagesData: List[M])(
      single: (M, String, Int) => Future[R]
  ): Future[BatchResponse[R]] = {

    val (succeeded, failed) = messagesData.zipWithIndex.foldLeft((List.empty[Future[R]], List.empty[Future[Failed]])) {
      case ((successful, failed), (messageData, index)) => {
        val id = messageData.Id

        try {
          (successful :+ single(messageData, id, index), failed)
        } catch {
          case e: SQSException =>
            (successful, failed :+ Future.successful(Failed(e.code, id, e.message, SenderFault = true)))
        }
      }
    }

    for {
      s <- Future.sequence(succeeded)
      f <- Future.sequence(failed)
    } yield BatchResponse(f, s)
  }
}

object BatchRequestsModule {

  /** In the given list of parameters, lookups all parameters of the form:
    * <code>{prefix}.{discriminator}.key=value</code>, and for each discriminator builds a map of found key-value
    * mappings.
    */
  def subParametersMaps[M](parameters: Map[String, String])(implicit
      flatParamsReader: BatchFlatParamsReader[M]
  ): List[M] = {
    val subParameters = collection.mutable.Map[String, Map[String, String]]()
    val keyRegexp = (Pattern.quote(flatParamsReader.batchPrefix) + "\\.([^.]+)\\.(.+)").r
    parameters.foreach { case (key, value) =>
      keyRegexp.findFirstMatchIn(key).map { keyMatch =>
        val discriminator = keyMatch.group(1)
        val subKey = keyMatch.group(2)

        val subMap =
          subParameters.getOrElse(discriminator, Map[String, String]())
        subParameters.put(discriminator, subMap + (subKey -> value))
      }
    }

    subParameters.toList.sortBy(_._1.toInt).map(_._2).map(flatParamsReader.read)
  }
}

trait BatchEntry {
  def Id: String
}
case class BatchRequest[M](
    Entries: List[M],
    QueueUrl: String
)

case class BatchResponse[R](Failed: List[Failed], Successful: List[R])

object BatchResponse {
  implicit def jsonFormat[R: JsonFormat]: RootJsonFormat[BatchResponse[R]] = jsonFormat2(BatchResponse.apply[R])
}
object BatchRequest {
  implicit def jsonFormat[M: JsonFormat]: RootJsonFormat[BatchRequest[M]] = jsonFormat2(BatchRequest.apply[M])

  implicit def queryParamReader[M: BatchFlatParamsReader]: FlatParamsReader[BatchRequest[M]] =
    new FlatParamsReader[BatchRequest[M]] {
      override def read(params: Map[String, String]): BatchRequest[M] = {
        new BatchRequest[M](
          BatchRequestsModule.subParametersMaps(params),
          requiredParameter(params)(QueueUrlParameter)
        )
      }
    }

}
case class Failed(Code: String, Id: String, Message: String, SenderFault: Boolean)

object Failed {
  implicit val format: RootJsonFormat[Failed] = jsonFormat4(Failed.apply)
}
