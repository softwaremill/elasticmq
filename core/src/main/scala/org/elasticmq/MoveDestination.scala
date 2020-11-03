package org.elasticmq

sealed trait MoveDestination
case object MoveToDLQ extends MoveDestination
