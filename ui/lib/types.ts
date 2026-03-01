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
