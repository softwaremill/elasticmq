package org.elasticmq.server

import com.weiglewilczek.slf4s.Logging

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  def start() {
    logger.info("Starting the ElasticMQ server ...")

    //logger.info("Configured value: " + config.value)

    logger.info("Welcome to ElasticMQ!")
  }
}
