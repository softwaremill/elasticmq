import { SQSClient } from '@aws-sdk/client-sqs';

export const sqsClient = new SQSClient({
  endpoint: process.env.NEXT_PUBLIC_SQS_ENDPOINT || 'http://localhost:9324',
  region: process.env.AWS_REGION || 'elasticmq',
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'x',
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'x',
  },
});

export function extractQueueName(queueUrl: string): string {
  const parts = queueUrl.split('/');
  return parts[parts.length - 1];
}
