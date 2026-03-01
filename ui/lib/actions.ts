'use server'

import {
  ListQueuesCommand,
  GetQueueAttributesCommand,
  SendMessageCommand,
  SendMessageBatchCommand,
  ReceiveMessageCommand,
  DeleteMessageCommand,
  MessageAttributeValue,
} from '@aws-sdk/client-sqs';
import { sqsClient, extractQueueName } from './sqs-client';
import {
  QueueData, QueueStats, QueueAttributes,
  SendMessageParams, SendMessageResult,
  SendBatchParams, SendBatchResult,
  ReceivedMessage, ReceiveMessagesParams,
} from './types';

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

export async function receiveMessages(params: ReceiveMessagesParams): Promise<ReceivedMessage[]> {
  try {
    const command = new ReceiveMessageCommand({
      QueueUrl: params.queueUrl,
      MaxNumberOfMessages: params.maxNumberOfMessages ?? 10,
      AttributeNames: ['All'],
      MessageAttributeNames: ['All'],
      ...(params.visibilityTimeout !== undefined && { VisibilityTimeout: params.visibilityTimeout }),
      ...(params.waitTimeSeconds !== undefined && { WaitTimeSeconds: params.waitTimeSeconds }),
    });

    const result = await sqsClient.send(command);

    return (result.Messages ?? []).map(msg => ({
      messageId: msg.MessageId ?? '',
      receiptHandle: msg.ReceiptHandle ?? '',
      body: msg.Body ?? '',
      md5OfBody: msg.MD5OfBody ?? '',
      md5OfMessageAttributes: msg.MD5OfMessageAttributes,
      systemAttributes: (msg.Attributes ?? {}) as Record<string, string>,
      messageAttributes: Object.fromEntries(
        Object.entries(msg.MessageAttributes ?? {}).map(([key, val]) => [
          key,
          {
            dataType: val.DataType ?? 'String',
            stringValue: val.StringValue,
            binaryValue: val.BinaryValue
              ? Buffer.from(val.BinaryValue).toString('base64')
              : undefined,
          },
        ])
      ),
    }));
  } catch (error) {
    console.error('Error receiving messages:', error);
    throw new Error(
      error instanceof Error ? error.message : 'Failed to receive messages'
    );
  }
}

export async function sendMessageBatch(params: SendBatchParams): Promise<SendBatchResult> {
  try {
    const entries = params.entries.map(entry => {
      const messageAttributes: Record<string, MessageAttributeValue> = {};
      for (const attr of entry.messageAttributes ?? []) {
        if (!attr.name.trim()) continue;
        messageAttributes[attr.name] = attr.dataType === 'Binary'
          ? { DataType: 'Binary', BinaryValue: Buffer.from(attr.value) }
          : { DataType: attr.dataType, StringValue: attr.value };
      }

      return {
        Id: entry.id,
        MessageBody: entry.messageBody,
        ...(entry.delaySeconds !== undefined && { DelaySeconds: entry.delaySeconds }),
        ...(Object.keys(messageAttributes).length > 0 && { MessageAttributes: messageAttributes }),
        ...(entry.messageGroupId && { MessageGroupId: entry.messageGroupId }),
        ...(entry.messageDeduplicationId && { MessageDeduplicationId: entry.messageDeduplicationId }),
        ...(params.awsTraceHeader && {
          MessageSystemAttributes: {
            AWSTraceHeader: { DataType: 'String', StringValue: params.awsTraceHeader },
          },
        }),
      };
    });

    const result = await sqsClient.send(new SendMessageBatchCommand({
      QueueUrl: params.queueUrl,
      Entries: entries,
    }));

    return {
      successful: result.Successful?.length ?? 0,
      failed: (result.Failed ?? []).map(f => ({
        id: f.Id ?? '',
        message: f.Message ?? f.Code ?? 'Unknown error',
      })),
    };
  } catch (error) {
    console.error('Error sending message batch:', error);
    throw new Error(error instanceof Error ? error.message : 'Failed to send batch');
  }
}

export async function deleteMessage(queueUrl: string, receiptHandle: string): Promise<void> {
  try {
    const command = new DeleteMessageCommand({ QueueUrl: queueUrl, ReceiptHandle: receiptHandle });
    await sqsClient.send(command);
  } catch (error) {
    console.error('Error deleting message:', error);
    throw new Error(
      error instanceof Error ? error.message : 'Failed to delete message'
    );
  }
}
