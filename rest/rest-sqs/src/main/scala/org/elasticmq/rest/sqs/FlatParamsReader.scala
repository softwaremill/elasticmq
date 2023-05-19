package org.elasticmq.rest.sqs

trait FlatParamsReader[A] {
  def read(params: Map[String, String]): A

  protected def requiredParameter(params: Map[String, String])(n: String): String =
    params.getOrElse(n, throw new SQSException(s"Missing required field: $n"))
}

object FlatParamsReader {
  def apply[A](implicit fpr: FlatParamsReader[A]): FlatParamsReader[A] = fpr
}
