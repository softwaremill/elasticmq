'use server'

import {
  ListQueuesCommand,
  GetQueueAttributesCommand,
  SendMessageCommand,
  MessageAttributeValue,
} from '@aws-sdk/client-sqs';
import { sqsClient, extractQueueName } from './sqs-client';
import { QueueData, QueueStats, QueueAttributes, SendMessageParams, SendMessageResult } from './types';

function parseQueueStats(attributes: Record<string, string>): QueueStats {
  return {
    approximateNumberOfMessages: parseInt(attributes.ApproximateNumberOfMessages || '0'),
    approximateNumberOfMessagesDelayed: parseInt(attributes.ApproximateNumberOfMessagesDelayed || '0'),
    approximateNumberOfMessagesNotVisible: parseInt(attributes.ApproximateNumberOfMessagesNotVisible || '0'),
  };
}

export async function getQueues(): Promise<QueueData[]> {
  try {
    // List all queues
    const listCommand = new ListQueuesCommand({});
    const listResult = await sqsClient.send(listCommand);

    if (!listResult.QueueUrls || listResult.QueueUrls.length === 0) {
      return [];
    }

    // Get attributes for each queue
    const queueDataPromises = listResult.QueueUrls.map(async (queueUrl) => {
      const attributesCommand = new GetQueueAttributesCommand({
        QueueUrl: queueUrl,
        AttributeNames: ['All'],
      });

      const attributesResult = await sqsClient.send(attributesCommand);
      const attributes = attributesResult.Attributes || {};

      return {
        name: extractQueueName(queueUrl),
        url: queueUrl,
        stats: parseQueueStats(attributes),
        attributes: attributes as QueueAttributes,
      };
    });

    return await Promise.all(queueDataPromises);
  } catch (error) {
    console.error('Error fetching queues:', error);
    throw new Error('Failed to fetch queues. Make sure ElasticMQ is running on localhost:9324');
  }
}

export async function getQueueDetails(queueName: string): Promise<QueueData | null> {
  try {
    // First, get the queue URL
    const listCommand = new ListQueuesCommand({ QueueNamePrefix: queueName });
    const listResult = await sqsClient.send(listCommand);

    if (!listResult.QueueUrls || listResult.QueueUrls.length === 0) {
      return null;
    }

    // Find exact match
    const queueUrl = listResult.QueueUrls.find(url => extractQueueName(url) === queueName);
    if (!queueUrl) {
      return null;
    }

    // Get queue attributes
    const attributesCommand = new GetQueueAttributesCommand({
      QueueUrl: queueUrl,
      AttributeNames: ['All'],
    });

    const attributesResult = await sqsClient.send(attributesCommand);
    const attributes = attributesResult.Attributes || {};

    return {
      name: queueName,
      url: queueUrl,
      stats: parseQueueStats(attributes),
      attributes: attributes as QueueAttributes,
    };
  } catch (error) {
    console.error('Error fetching queue details:', error);
    throw new Error('Failed to fetch queue details. Make sure ElasticMQ is running on localhost:9324');
  }
}

export async function sendMessage(params: SendMessageParams): Promise<SendMessageResult> {
  try {
    // Build MessageAttributes map
    const messageAttributes: Record<string, MessageAttributeValue> = {};
    for (const attr of params.messageAttributes ?? []) {
      if (!attr.name.trim()) continue;
      if (attr.dataType === 'Binary') {
        messageAttributes[attr.name] = {
          DataType: 'Binary',
          BinaryValue: Buffer.from(attr.value),
        };
      } else {
        messageAttributes[attr.name] = {
          DataType: attr.dataType,
          StringValue: attr.value,
        };
      }
    }

    const command = new SendMessageCommand({
      QueueUrl: params.queueUrl,
      MessageBody: params.messageBody,
      ...(params.delaySeconds !== undefined && { DelaySeconds: params.delaySeconds }),
      ...(Object.keys(messageAttributes).length > 0 && { MessageAttributes: messageAttributes }),
      ...(params.awsTraceHeader && {
        MessageSystemAttributes: {
          AWSTraceHeader: { DataType: 'String', StringValue: params.awsTraceHeader },
        },
      }),
      ...(params.messageGroupId && { MessageGroupId: params.messageGroupId }),
      ...(params.messageDeduplicationId && { MessageDeduplicationId: params.messageDeduplicationId }),
    });

    const result = await sqsClient.send(command);
    return {
      messageId: result.MessageId ?? '',
      sequenceNumber: result.SequenceNumber,
    };
  } catch (error) {
    console.error('Error sending message:', error);
    throw new Error(
      error instanceof Error ? error.message : 'Failed to send message'
    );
  }
}
