package org.elasticmq.persistence.file

import com.typesafe.config.Config
import org.elasticmq.util.Logging

/** Resolves configuration keys renamed in 2.0 (camelCase to kebab-case). The old key is still read, with a deprecation
  * warning, and takes precedence when present, so a 1.x configuration file keeps working unchanged.
  */
object DeprecatedConfigKeys extends Logging {

  /** @return
    *   the path to read from `config`: `deprecatedKey` if it is set (logging a warning), otherwise `key` if it is set.
    */
  def resolve(config: Config, key: String, deprecatedKey: String): Option[String] =
    if (config.hasPath(deprecatedKey)) {
      val origin = config.getValue(deprecatedKey).origin().description()
      logger.warn(
        s"Configuration key '$deprecatedKey' ($origin) is deprecated and will be removed in a future release, use '$key' instead"
      )
      Some(deprecatedKey)
    } else if (config.hasPath(key)) Some(key)
    else None
}
