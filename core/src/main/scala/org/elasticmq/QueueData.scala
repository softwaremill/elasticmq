package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(name: String,
                     defaultVisibilityTimeout: MillisVisibilityTimeout,
                     delay: Duration,
                     created: DateTime,
                     lastModified: DateTime)

