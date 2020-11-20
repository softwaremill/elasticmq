interface QueueMessagesData {
    queueName: string;
    currentMessagesNumber: number;
    delayedMessagesNumber: number;
    notVisibleMessagesNumber: number;
    isOpened: boolean
}

interface QueueStatistic {
    name: string;
    statistics: Statistics;
}

interface QueueRedrivePolicyAttribute {
    deadLetterTargetArn: string,
    maxReceiveCount: number
}

interface Statistics {
    approximateNumberOfVisibleMessages: number;
    approximateNumberOfMessagesDelayed: number;
    approximateNumberOfInvisibleMessages: number;
}

export type {QueueMessagesData, QueueStatistic, QueueRedrivePolicyAttribute}