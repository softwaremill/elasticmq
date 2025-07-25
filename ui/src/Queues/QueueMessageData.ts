interface QueueMessagesData {
  queueName: string;
  currentMessagesNumber: number;
  delayedMessagesNumber: number;
  notVisibleMessagesNumber: number;
  isOpened: boolean;
  messages?: QueueMessage[];
  messagesLoading?: boolean;
  messagesError?: string | null;
}

interface QueueStatistic {
  name: string;
  statistics: Statistics;
}

interface Statistics {
  approximateNumberOfVisibleMessages: number;
  approximateNumberOfMessagesDelayed: number;
  approximateNumberOfInvisibleMessages: number;
}

interface QueueRedrivePolicyAttribute {
  deadLetterTargetArn: string;
  maxReceiveCount: number;
}

interface QueueMessage {
  messageId: string;
  body: string;
  sentTimestamp: string;
  receiptHandle?: string;
  attributes?: { [key: string]: string };
  messageAttributes?: { [key: string]: any };
  isExpanded?: boolean;
}

export type {
  QueueMessagesData,
  QueueStatistic,
  QueueRedrivePolicyAttribute,
  QueueMessage,
};
