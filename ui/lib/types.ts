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

export interface ReceivedMessageAttribute {
  dataType: string;
  stringValue?: string;
  binaryValue?: string; // base64
}

export interface ReceivedMessage {
  messageId: string;
  receiptHandle: string;
  body: string;
  md5OfBody: string;
  md5OfMessageAttributes?: string;
  systemAttributes: Record<string, string>;     // Attributes (SenderId, SentTimestamp, etc.)
  messageAttributes: Record<string, ReceivedMessageAttribute>;
}

export interface BatchEntry {
  id: string;             // batch-local unique ID (e.g. "msg-0")
  messageBody: string;    // already $i-substituted
  delaySeconds?: number;
  messageAttributes?: MessageAttributeEntry[];
  messageGroupId?: string;
  messageDeduplicationId?: string;
}

export interface SendBatchParams {
  queueUrl: string;
  entries: BatchEntry[];
  awsTraceHeader?: string;
}

export interface SendBatchResult {
  successful: number;
  failed: { id: string; message: string }[];
}

export interface ReceiveMessagesParams {
  queueUrl: string;
  maxNumberOfMessages?: number;   // 1–10
  visibilityTimeout?: number;     // 0–43200 seconds
  waitTimeSeconds?: number;       // 0–20 seconds (long polling)
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
