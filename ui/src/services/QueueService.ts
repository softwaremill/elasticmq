import * as Yup from "yup";
import {
  QueueRedrivePolicyAttribute,
  QueueStatistic,
  QueueMessage,
} from "../Queues/QueueMessageData";
import axios from "axios";

const instance =
  !process.env.NODE_ENV || process.env.NODE_ENV === "development"
    ? axios.create({ baseURL: "http://localhost:9325/" })
    : axios;

const sqsInstance =
  !process.env.NODE_ENV || process.env.NODE_ENV === "development"
    ? axios.create({ baseURL: "/" })
    : axios;

const queuesBasicInformationSchema = Yup.array().of(
  Yup.object()
    .required()
    .shape({
      name: Yup.string().required("Required queueName"),
      statistics: Yup.object()
        .required("Statistics are required")
        .shape({
          approximateNumberOfVisibleMessages: Yup.number().required(
            "Required approximateNumberOfVisibleMessages"
          ),
          approximateNumberOfMessagesDelayed: Yup.number().required(
            "Required approximateNumberOfMessagesDelayed"
          ),
          approximateNumberOfInvisibleMessages: Yup.number().required(
            "Required approximateNumberOfInvisibleMessages"
          ),
        }),
    })
);

async function getQueueListWithCorrelatedMessages(): Promise<QueueStatistic[]> {
  const response = await instance.get(`statistics/queues`);
  const result = queuesBasicInformationSchema.validateSync(response.data);
  return result === undefined ? [] : (result as QueueStatistic[]);
}

const numberOfMessagesRelatedAttributes = [
  "ApproximateNumberOfMessages",
  "ApproximateNumberOfMessagesNotVisible",
  "ApproximateNumberOfMessagesDelayed",
];

interface QueueAttributes {
  attributes: AttributeNameValue;
  name: string;
}

interface AttributeNameValue {
  [name: string]: string;
}

async function getQueueAttributes(queueName: string) {
  const response = await instance.get(`statistics/queues/${queueName}`);
  if (response.status !== 200) {
    console.log(
      "Can't obtain attributes of " +
        queueName +
        " queue because of " +
        response.statusText
    );
    return [];
  }
  const data: QueueAttributes = response.data as QueueAttributes;
  return Object.entries(data.attributes)
    .filter(
      ([attributeKey, _]) =>
        !numberOfMessagesRelatedAttributes.includes(attributeKey)
    )
    .map(([attributeKey, attributeValue]) => [
      attributeKey,
      trimAttributeValue(attributeKey, attributeValue),
    ]);
}

function trimAttributeValue(attributeName: string, attributeValue: string) {
  switch (attributeName) {
    case "CreatedTimestamp":
    case "LastModifiedTimestamp":
      return new Date(parseInt(attributeValue) * 1000).toISOString();
    case "RedrivePolicy":
      const redriveAttributeValue: QueueRedrivePolicyAttribute =
        JSON.parse(attributeValue);
      const deadLetterTargetArn =
        "DeadLetterTargetArn: " + redriveAttributeValue.deadLetterTargetArn;
      const maxReceiveCount =
        "MaxReceiveCount: " + redriveAttributeValue.maxReceiveCount;
      return deadLetterTargetArn + ", " + maxReceiveCount;
    default:
      return attributeValue;
  }
}

async function sendMessage(
  queueName: string,
  messageBody: string
): Promise<void> {
  const params = new URLSearchParams();
  params.append("Action", "SendMessage");
  params.append("MessageBody", messageBody);

  const response = await sqsInstance.post(`queue/${queueName}`, params, {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
  });

  if (response.status !== 200 && response.status !== 201) {
    throw new Error(`Failed to send message: ${response.statusText}`);
  }
}

async function getQueueMessages(
  queueName: string,
  maxResults: number = 10
): Promise<QueueMessage[]> {
  try {
    const params = new URLSearchParams();
    params.append("Action", "ReceiveMessage");
    params.append("MaxNumberOfMessages", maxResults.toString());
    params.append("VisibilityTimeout", "0");
    params.append("WaitTimeSeconds", "0");

    const response = await sqsInstance.post(`queue/${queueName}`, params, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    if (response.status !== 200) {
      console.log(
        `Can't obtain messages from ${queueName} queue: ${response.statusText}`
      );
      return [];
    }

    const xmlData = response.data;
    return parseReceiveMessageResponse(xmlData);
  } catch (error) {
    console.log(`Error fetching messages from ${queueName}:`, error);
    return [];
  }
}

function parseReceiveMessageResponse(xmlData: string): QueueMessage[] {
  try {
    const messages: QueueMessage[] = [];

    const messageRegex = /<Message>([\s\S]*?)<\/Message>/g;
    const messageIdRegex = /<MessageId>([\s\S]*?)<\/MessageId>/;
    const receiptHandleRegex = /<ReceiptHandle>([\s\S]*?)<\/ReceiptHandle>/;
    const bodyRegex = /<Body>([\s\S]*?)<\/Body>/;
    const sentTimestampRegex =
      /<Name>SentTimestamp<\/Name>\s*<Value>([\s\S]*?)<\/Value>/;

    let match;
    while ((match = messageRegex.exec(xmlData)) !== null) {
      const messageXml = match[1];

      const messageIdMatch = messageIdRegex.exec(messageXml);
      const receiptHandleMatch = receiptHandleRegex.exec(messageXml);
      const bodyMatch = bodyRegex.exec(messageXml);
      const sentTimestampMatch = sentTimestampRegex.exec(messageXml);

      if (messageIdMatch && bodyMatch) {
        messages.push({
          messageId: messageIdMatch[1],
          receiptHandle: receiptHandleMatch ? receiptHandleMatch[1] : undefined,
          body: bodyMatch[1],
          sentTimestamp: sentTimestampMatch
            ? sentTimestampMatch[1]
            : new Date().toISOString(),
          attributes: {},
          messageAttributes: {},
        });
      }
    }

    return messages;
  } catch (error) {
    console.error("Error parsing XML response:", error);
    return [];
  }
}

async function deleteMessage(
  queueName: string,
  messageId: string,
  receiptHandle: string
): Promise<void> {
  try {
    const params = new URLSearchParams();
    params.append("Action", "DeleteMessage");
    params.append("ReceiptHandle", receiptHandle);

    const response = await sqsInstance.post(`queue/${queueName}`, params, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    if (response.status !== 200) {
      throw new Error(`Failed to delete message: ${response.statusText}`);
    }
  } catch (error) {
    console.error(
      `Error deleting message ${messageId} from ${queueName}:`,
      error
    );
    throw error;
  }
}

export default {
  getQueueListWithCorrelatedMessages,
  getQueueAttributes,
  sendMessage,
  getQueueMessages,
  deleteMessage,
};
