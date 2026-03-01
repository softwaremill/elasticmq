export type MessageAttributeDataType = 'String' | 'Number' | 'Binary' | string;

export interface MessageAttributeEntry {
  name: string;
  dataType: MessageAttributeDataType;
  value: string;
}

export interface SendMessageParams {
  queueUrl: string;
  messageBody: string;
  delaySeconds?: number;
  messageAttributes?: MessageAttributeEntry[];
  awsTraceHeader?: string;
  // FIFO-only
  messageGroupId?: string;
  messageDeduplicationId?: string;
}

export interface SendMessageResult {
  messageId: string;
  sequenceNumber?: string;
}

export interface QueueAttributes {
  [key: string]: string;
}

export interface QueueStats {
  approximateNumberOfMessages: number;
  approximateNumberOfMessagesDelayed: number;
  approximateNumberOfMessagesNotVisible: number;
}

export interface QueueData {
  name: string;
  url: string;
  stats: QueueStats;
  attributes: QueueAttributes;
}
