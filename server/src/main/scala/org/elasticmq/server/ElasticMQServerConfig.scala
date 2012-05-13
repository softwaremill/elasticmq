package org.elasticmq.server

import com.twitter.util.Config
import com.twitter.ostrich.admin.RuntimeEnvironment

class ElasticMQServerConfig extends Config[RuntimeEnvironment => ElasticMQServer] {
  var value = 10

  def apply() = { (runtime: RuntimeEnvironment) =>
    new ElasticMQServer(this)
  }
}
