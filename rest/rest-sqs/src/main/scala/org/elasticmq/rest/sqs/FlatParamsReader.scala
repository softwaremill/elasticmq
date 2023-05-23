package org.elasticmq.rest.sqs

trait FlatParamsReader[A] {
  def read(params: Map[String, String]): A

  protected def requiredParameter(params: Map[String, String])(n: String): String =
    params.getOrElse(n, throw new SQSException(s"Missing required field: $n"))

  protected def optionalParameter(params: Map[String, String])(n: String): Option[String] =
    params.get(n)
}

object BatchFlatParamsReader {
  def apply[A](implicit fpr: BatchFlatParamsReader[A]): BatchFlatParamsReader[A] = fpr
}

trait BatchFlatParamsReader[A] extends FlatParamsReader[A] {
  def batchPrefix: String
}
