import * as Yup from "yup";
import {QueueRedrivePolicyAttribute, QueueStatistic} from "../Queues/QueueMessageData";
import axios from "axios";

const instance = (!process.env.NODE_ENV || process.env.NODE_ENV === 'development') ? axios.create({baseURL: "http://localhost:9325/"}) : axios;

const queuesBasicInformationSchema: Yup.NotRequiredArraySchema<QueueStatistic> = Yup.array().of(
    Yup.object().required().shape({
        name: Yup.string().required("Required queueName"),
        statistics: Yup.object().required("Statistics are required").shape({
            approximateNumberOfVisibleMessages: Yup.number().required("Required approximateNumberOfVisibleMessages"),
            approximateNumberOfMessagesDelayed: Yup.number().required("Required approximateNumberOfMessagesDelayed"),
            approximateNumberOfInvisibleMessages: Yup.number().required("Required approximateNumberOfInvisibleMessages")
        })
    })
);

async function getQueueListWithCorrelatedMessages(): Promise<QueueStatistic[]> {
    const response = await instance.get(`statistics/queues`)
    const result = queuesBasicInformationSchema.validateSync(response.data)
    return result === undefined ? [] : result;
}

const numberOfMessagesRelatedAttributes = [
    "ApproximateNumberOfMessages",
    "ApproximateNumberOfMessagesNotVisible",
    "ApproximateNumberOfMessagesDelayed",
]

interface QueueAttributes {
    attributes: AttributeNameValue,
    name: string
}

interface AttributeNameValue {
    [name: string]: string;
}

async function getQueueAttributes(queueName: string) {
    const response = await instance.get(`statistics/queues/${queueName}`)
    if (response.status !== 200) {
        console.log("Can't obtain attributes of " + queueName + " queue because of " + response.statusText)
        return [];
    }
    const data: QueueAttributes = response.data as QueueAttributes
    return Object.keys(data.attributes).filter(attributeKey => !numberOfMessagesRelatedAttributes.includes(attributeKey)).map(attributeKey => {
        const attributeValue = data.attributes[attributeKey];
        return [
            attributeKey,
            trimAttributeValue(attributeKey, attributeValue)
        ]
    })
}

function trimAttributeValue(attributeName: string, attributeValue: string) {
    switch (attributeName) {
        case "CreatedTimestamp":
        case "LastModifiedTimestamp":
            return new Date(parseInt(attributeValue) * 1000).toISOString();
        case "RedrivePolicy":
            const redriveAttributeValue: QueueRedrivePolicyAttribute = JSON.parse(attributeValue)
            const deadLetterTargetArn = "DeadLetterTargetArn: " + redriveAttributeValue.deadLetterTargetArn
            const maxReceiveCount = "MaxReceiveCount: " + redriveAttributeValue.maxReceiveCount
            return deadLetterTargetArn + ", " + maxReceiveCount
        default:
            return attributeValue;

    }
}

export default {getQueueListWithCorrelatedMessages, getQueueAttributes}