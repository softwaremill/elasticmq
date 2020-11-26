import {useEffect, useState} from "react";
import QueueService from "../services/QueueService";
import {QueueMessagesData, QueueStatistic} from "./QueueMessageData";

function useRefreshedQueueStatistics(): QueueMessagesData[] {
    function convertQueueStatisticsToNewQueueData(newQuery: QueueStatistic) {
        return {
            queueName: newQuery.name,
            currentMessagesNumber: newQuery.statistics.approximateNumberOfVisibleMessages,
            delayedMessagesNumber: newQuery.statistics.approximateNumberOfMessagesDelayed,
            notVisibleMessagesNumber: newQuery.statistics.approximateNumberOfInvisibleMessages,
            isOpened: false
        } as QueueMessagesData
    }

    function updateNumberOfMessagesInQueue(newStatistics: QueueStatistic, knownQuery: QueueMessagesData) {
        return {
            ...knownQuery,
            currentMessagesNumber: newStatistics.statistics.approximateNumberOfVisibleMessages,
            delayedMessagesNumber: newStatistics.statistics.approximateNumberOfMessagesDelayed,
            notVisibleMessagesNumber: newStatistics.statistics.approximateNumberOfInvisibleMessages,
        }
    }

    const [queuesOverallData, setQueuesOverallData] = useState<QueueMessagesData[]>([]);
    useEffect(() => {
        function obtainInitialStatistics() {
            return QueueService.getQueueListWithCorrelatedMessages().then(queuesStatistics =>
                queuesStatistics.map(convertQueueStatisticsToNewQueueData)
            );
        }

        function getQueuesListWithMessages() {
            QueueService.getQueueListWithCorrelatedMessages()
                .then(statistics => {
                    setQueuesOverallData((prevState) => {
                        return statistics.map(queueStatistics => {
                            const maybeKnownQuery = prevState.find(queueMessageData => queueMessageData.queueName === queueStatistics.name)
                            if (maybeKnownQuery === undefined) {
                                return convertQueueStatisticsToNewQueueData(queueStatistics)
                            } else {
                                return updateNumberOfMessagesInQueue(queueStatistics, maybeKnownQuery)
                            }
                        })
                    })
                })
        }

        const fetchInitialStatistics = async () => {
            const initialStatistics = await obtainInitialStatistics()
            setQueuesOverallData((prevState) => {
                if (prevState.length === 0) {
                    return initialStatistics
                } else {
                    return prevState;
                }
            })
        }

        fetchInitialStatistics()

        const interval = setInterval(() => {
            getQueuesListWithMessages()
        }, 1000);
        return () => {
            clearInterval(interval);
        };
    }, []);

    return queuesOverallData;
}

export default useRefreshedQueueStatistics;