CREATE TABLE `emq_queue` (
  `id` varchar(128) NOT NULL,
  `last_modified_timestamp` bigint(20) NOT NULL,
  `created_timestamp` bigint(20) NOT NULL,
  `default_visibility_timeout` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE `emq_message` (
  `next_delivery` bigint(20) NOT NULL,
  `id` varchar(128) NOT NULL,
  `queue_name` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `created_timestamp` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE `emq_msg_stats` (
  `approximate_first_receive` bigint(20) NOT NULL,
  `id` varchar(128) NOT NULL,
  `approximate_receive_count` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;