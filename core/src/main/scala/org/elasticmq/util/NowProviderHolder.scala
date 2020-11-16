package org.elasticmq.util

trait NowProviderHolder {

  val nowProvider: NowProvider = new NowProvider
}
