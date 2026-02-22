import {
    CreateQueueCommand,
    SendMessageCommand,
    ReceiveMessageCommand,
    ListQueuesCommand, GetQueueUrlCommand
} from '@aws-sdk/client-sqs';
import { ElasticMqContainer } from '../src/elasticmq-container.js';

describe('ElasticMQ Basic Operations', () => {
  let container;
  let sqsClient;

  beforeAll(async () => {
    // Container automatically detects local server mode from environment variables
    container = new ElasticMqContainer('messages-storage');
    await container.start();
    sqsClient = container.createSqsClient();
  });

  afterAll(async () => {
    await container.stop();
  });

  test('should create a queue', async () => {
    const response = await sqsClient.send(
      new CreateQueueCommand({
        QueueName: 'test-queue'
      })
    );

    expect(response.QueueUrl).toBeDefined();
    expect(response.QueueUrl).toContain('test-queue');
  });

  test('should send and receive messages', async () => {
    // Create queue
    const createResponse = await sqsClient.send(
      new CreateQueueCommand({
        QueueName: 'message-queue'
      })
    );
    const queueUrl = createResponse.QueueUrl;

    // Send message
    await sqsClient.send(
      new SendMessageCommand({
        QueueUrl: queueUrl,
        MessageBody: 'Test message'
      })
    );

    // Receive message
    const receiveResponse = await sqsClient.send(
      new ReceiveMessageCommand({
        QueueUrl: queueUrl,
        MaxNumberOfMessages: 1
      })
    );

    expect(receiveResponse.Messages).toHaveLength(1);
    expect(receiveResponse.Messages[0].Body).toBe('Test message');
  });

  test('should list queues', async () => {
    // Create a couple of queues
    await sqsClient.send(
      new CreateQueueCommand({ QueueName: 'queue-1' })
    );
    await sqsClient.send(
      new CreateQueueCommand({ QueueName: 'queue-2' })
    );

    // List queues
    const response = await sqsClient.send(new ListQueuesCommand({}));

    expect(response.QueueUrls.length).toBeGreaterThanOrEqual(2);
  });

  test('should handle message attributes', async () => {
    const createResponse = await sqsClient.send(
      new CreateQueueCommand({
        QueueName: 'attributes-queue'
      })
    );
    const queueUrl = createResponse.QueueUrl;

    // Send message with attributes
    await sqsClient.send(
      new SendMessageCommand({
        QueueUrl: queueUrl,
        MessageBody: 'Message with attributes',
        MessageAttributes: {
          'Author': {
            DataType: 'String',
            StringValue: 'TestUser'
          },
          'Priority': {
            DataType: 'Number',
            StringValue: '1'
          }
        }
      })
    );

    // Receive message with attributes
    const receiveResponse = await sqsClient.send(
      new ReceiveMessageCommand({
        QueueUrl: queueUrl,
        MaxNumberOfMessages: 1,
        MessageAttributeNames: ['All']
      })
    );

    expect(receiveResponse.Messages).toHaveLength(1);
    expect(receiveResponse.Messages[0].MessageAttributes).toBeDefined();
    expect(receiveResponse.Messages[0].MessageAttributes.Author.StringValue).toBe('TestUser');
    expect(receiveResponse.Messages[0].MessageAttributes.Priority.StringValue).toBe('1');
  });

  test('should fail getting non-existent queue url', async () => {
      try {
          await sqsClient.send(
              new GetQueueUrlCommand({QueueName: "non-existent-queue"})
          );
      } catch (e) {
          expect(e.name).toBe('QueueDoesNotExist');
      }
  })
});
