import {Dispatch, SetStateAction, useEffect, useState} from "react";
import QueueService from "../services/QueueService";
import {QueueMessagesData, QueueStatistic} from "./QueueMessageData";

function useRefreshedQueueStatistics(): QueuesStatisticsDataControl {
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

    async function getInitialStatistics() {
        return QueueService.getQueueListWithCorrelatedMessages().then(QueuesStatistics => {
            return QueuesStatistics.map(queueStatistics => convertQueueStatisticsToNewQueueData(queueStatistics));
        });
    }

    const [queuesOverallData, setQueuesOverallData] = useState<QueueMessagesData[]>([]);
    useEffect(() => {
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
        const interval = setInterval(() => {
            getQueuesListWithMessages()
        }, 1000);
        return () => {
            clearInterval(interval);
        };
    }, []);

    return {
        queuesOverallData: queuesOverallData,
        setQueuesOverallData: setQueuesOverallData,
        obtainInitialStatistics: getInitialStatistics
    } as QueuesStatisticsDataControl
}

interface QueuesStatisticsDataControl {
    queuesOverallData: QueueMessagesData[]
    setQueuesOverallData: Dispatch<SetStateAction<QueueMessagesData[]>>
    obtainInitialStatistics: () => Promise<QueueMessagesData[]>
}

export default {useRefreshQueueData: useRefreshedQueueStatistics}
export type {QueuesStatisticsDataControl}