import axios from "axios";
import QueueService from "./QueueService";

jest.mock("axios");

afterEach(() => {
    jest.clearAllMocks();
})

test("Get queues statistics should return basic information about messages in queues", async () => {
    const data =
        [
            {
                name: "queueName1",
                statistics: {
                    approximateNumberOfVisibleMessages: 5,
                    approximateNumberOfMessagesDelayed: 8,
                    approximateNumberOfInvisibleMessages: 10
                }
            },
            {
                name: "queueName2",
                statistics: {
                    approximateNumberOfVisibleMessages: 1,
                    approximateNumberOfMessagesDelayed: 3,
                    approximateNumberOfInvisibleMessages: 7
                }
            }
        ];

    (axios.get as jest.Mock).mockResolvedValueOnce({data})

    await expect(QueueService.getQueuesBasicInformation()).resolves.toEqual(data)
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Get queue statistics should return empty array if no queues are defined", async () => {
    const data: Array<any> = [];

    (axios.get as jest.Mock).mockResolvedValueOnce({data});

    await expect(QueueService.getQueuesBasicInformation()).resolves.toEqual(data);
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Get queue statistics should return validation error if queue is missing name property", async () => {
    expect.assertions(2);

    const data =
        [
            {
                statistics: {
                    approximateNumberOfVisibleMessages: 5,
                    approximateNumberOfMessagesDelayed: 8,
                    approximateNumberOfInvisibleMessages: 10
                }
            }
        ];
    (axios.get as jest.Mock).mockResolvedValueOnce({data});

    try {
        await QueueService.getQueuesBasicInformation();
    } catch (e) {
        expect(e.errors).toEqual([
            "Required queueName"
        ]);
    }
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Get queue statistics should return validation error if queue is missing approximate number of visible messages property", async () => {
    expect.assertions(2);

    const data =
        [
            {
                queueName: "name1",
                statistics: {
                    approximateNumberOfMessagesDelayed: 8,
                    approximateNumberOfInvisibleMessages: 10
                }
            }
        ];
    (axios.get as jest.Mock).mockResolvedValueOnce({data});

    try {
        await QueueService.getQueuesBasicInformation();
    } catch (e) {
        expect(e.errors).toEqual([
            "Required approximateNumberOfVisibleMessages"
        ]);
    }
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Get queue statistics should return validation error if queue is missing approximate number of delayed messages property", async () => {
    expect.assertions(2);

    const data =
        [
            {
                queueName: "name1",
                statistics: {
                    approximateNumberOfVisibleMessages: 5,
                    approximateNumberOfInvisibleMessages: 10
                }
            }
        ];
    (axios.get as jest.Mock).mockResolvedValueOnce({data});

    try {
        await QueueService.getQueuesBasicInformation();
    } catch (e) {
        expect(e.errors).toEqual([
            "Required approximateNumberOfMessagesDelayed"
        ]);
    }
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Get queue statistics should return validation error if queue is missing approximate number of invisible messages property", async () => {
    expect.assertions(2);

    const data =
        [
            {
                queueName: "name1",
                statistics: {
                    approximateNumberOfVisibleMessages: 5,
                    approximateNumberOfMessagesDelayed: 8
                }
            }
        ];
    (axios.get as jest.Mock).mockResolvedValueOnce({data});

    try {
        await QueueService.getQueuesBasicInformation();
    } catch (e) {
        expect(e.errors).toEqual([
            "Required approximateNumberOfInvisibleMessages"
        ]);
    }
    expect(axios.get).toBeCalledWith("statistics/queues")
})

test("Getting queue attributes should return empty array if it can't be found", async () => {
    expect.assertions(2);

    (axios.get as jest.Mock).mockResolvedValueOnce({status: 404})

    await expect(QueueService.getQueueAttributes("queueName")).resolves.toEqual([]);
    expect(axios.get).toBeCalledWith("statistics/queues/queueName");
})

test("Timestamp related attributes should be converted to human readable dates", async () => {
    const data = {
        name: "QueueName",
        attributes: {
            CreatedTimestamp: "1605539328",
            LastModifiedTimestamp: "1605539300"
        }
    };

    (axios.get as jest.Mock).mockResolvedValueOnce({status: 200, data: data})

    await expect(QueueService.getQueueAttributes("QueueName")).resolves.toEqual([
        ["CreatedTimestamp", "2020-11-16T15:08:48.000Z"],
        ["LastModifiedTimestamp", "2020-11-16T15:08:20.000Z"]
    ])
    expect(axios.get).toBeCalledWith("statistics/queues/QueueName");
})

test("RedrivePolicy attribute should be converted to easier to read format", async () => {
    const data = {
        name: "QueueName",
        attributes: {
            RedrivePolicy: "{\"deadLetterTargetArn\": \"targetArn\", \"maxReceiveCount\": 10}"
        }
    };

    (axios.get as jest.Mock).mockResolvedValueOnce({status: 200, data: data})

    await expect(QueueService.getQueueAttributes("QueueName")).resolves.toEqual([
        ["RedrivePolicy", "DeadLetterTargetArn: targetArn, MaxReceiveCount: 10"]
    ])
    expect(axios.get).toBeCalledWith("statistics/queues/QueueName");
})

test("Attributes related to amount of messages should be filtered out", async () => {
    const data = {
        name: "QueueName",
        attributes: {
            ApproximateNumberOfMessages: 10,
            ApproximateNumberOfMessagesNotVisible: 5,
            ApproximateNumberOfMessagesDelayed: 8,
            RandomAttribute: "09203"
        }
    };

    (axios.get as jest.Mock).mockResolvedValueOnce({status: 200, data: data})

    await expect(QueueService.getQueueAttributes("QueueName")).resolves.toEqual([
        ["RandomAttribute", "09203"]
    ])
    expect(axios.get).toBeCalledWith("statistics/queues/QueueName");
})