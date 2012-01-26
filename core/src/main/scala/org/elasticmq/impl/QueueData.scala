package org.elasticmq.impl

import org.joda.time.{Duration, DateTime}
import org.elasticmq.MillisVisibilityTimeout

case class QueueData(name: String,
                     defaultVisibilityTimeout: MillisVisibilityTimeout,
                     delay: Duration,
                     created: DateTime,
                     lastModified: DateTime)

