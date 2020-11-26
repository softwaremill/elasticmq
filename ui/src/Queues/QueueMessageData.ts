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

interface Statistics {
    approximateNumberOfVisibleMessages: number;
    approximateNumberOfMessagesDelayed: number;
    approximateNumberOfInvisibleMessages: number;
}

interface QueueRedrivePolicyAttribute {
    deadLetterTargetArn: string,
    maxReceiveCount: number
}

export type {QueueMessagesData, QueueStatistic, QueueRedrivePolicyAttribute}