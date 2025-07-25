import { useEffect, useState, useCallback } from "react";
import QueueService from "../services/QueueService";
import { QueueMessagesData, QueueStatistic } from "./QueueMessageData";

function useRefreshedQueueStatistics(): {
  queuesData: QueueMessagesData[];
  fetchQueueMessages: (queueName: string) => Promise<void>;
  deleteMessage: (
    queueName: string,
    messageId: string,
    receiptHandle: string
  ) => Promise<void>;
  updateMessageExpandedState: (
    queueName: string,
    messageId: string | null
  ) => void;
} {
  function convertQueueStatisticsToNewQueueData(newQuery: QueueStatistic) {
    return {
      queueName: newQuery.name,
      currentMessagesNumber:
        newQuery.statistics.approximateNumberOfVisibleMessages,
      delayedMessagesNumber:
        newQuery.statistics.approximateNumberOfMessagesDelayed,
      notVisibleMessagesNumber:
        newQuery.statistics.approximateNumberOfInvisibleMessages,
      isOpened: false,
      messages: [],
      messagesLoading: false,
      messagesError: null,
    } as QueueMessagesData;
  }

  function updateNumberOfMessagesInQueue(
    newStatistics: QueueStatistic,
    knownQuery: QueueMessagesData
  ) {
    return {
      ...knownQuery,
      currentMessagesNumber:
        newStatistics.statistics.approximateNumberOfVisibleMessages,
      delayedMessagesNumber:
        newStatistics.statistics.approximateNumberOfMessagesDelayed,
      notVisibleMessagesNumber:
        newStatistics.statistics.approximateNumberOfInvisibleMessages,
    };
  }

  const [queuesOverallData, setQueuesOverallData] = useState<
    QueueMessagesData[]
  >([]);

  const fetchQueueMessages = useCallback(async (queueName: string) => {
    const updateQueue = (
      updater: (queue: QueueMessagesData) => QueueMessagesData
    ) => {
      setQueuesOverallData((prevQueues) =>
        prevQueues.map((queue) =>
          queue.queueName === queueName ? updater(queue) : queue
        )
      );
    };

    updateQueue((queue) => ({
      ...queue,
      messagesLoading: true,
      messagesError: null,
    }));

    try {
      const messages = await QueueService.getQueueMessages(queueName, 10);

      updateQueue((queue) => ({
        ...queue,
        messages,
        messagesLoading: false,
        messagesError: null,
      }));
    } catch (error) {
      console.error("Error fetching messages:", error);

      updateQueue((queue) => ({
        ...queue,
        messagesLoading: false,
        messagesError:
          error instanceof Error
            ? error.message
            : "Failed to fetch messages. Please try again.",
      }));
    }
  }, []);

  const updateMessageExpandedState = (
    queueName: string,
    messageId: string | null
  ) => {
    setQueuesOverallData((prevQueues) =>
      prevQueues.map((queue) => {
        if (queue.queueName !== queueName) return queue;

        return {
          ...queue,
          messages: queue.messages?.map((msg) => ({
            ...msg,
            isExpanded:
              msg.messageId === messageId ? !msg.isExpanded : msg.isExpanded,
          })),
        };
      })
    );
  };

  const deleteMessage = useCallback(
    async (queueName: string, messageId: string, receiptHandle: string) => {
      try {
        await QueueService.deleteMessage(queueName, messageId, receiptHandle);

        await fetchQueueMessages(queueName);
      } catch (error) {
        console.error("Erro ao deletar mensagem:", error);
      }
    },
    []
  );

  useEffect(() => {
    function obtainInitialStatistics() {
      return QueueService.getQueueListWithCorrelatedMessages().then(
        (queuesStatistics) =>
          queuesStatistics.map(convertQueueStatisticsToNewQueueData)
      );
    }

    function getQueuesListWithMessages() {
      QueueService.getQueueListWithCorrelatedMessages().then((statistics) => {
        setQueuesOverallData((prevState) => {
          return statistics.map((queueStatistics) => {
            const maybeKnownQuery = prevState.find(
              (queueMessageData) =>
                queueMessageData.queueName === queueStatistics.name
            );
            if (maybeKnownQuery === undefined) {
              return convertQueueStatisticsToNewQueueData(queueStatistics);
            } else {
              return updateNumberOfMessagesInQueue(
                queueStatistics,
                maybeKnownQuery
              );
            }
          });
        });
      });
    }

    const fetchInitialStatistics = async () => {
      const initialStatistics = await obtainInitialStatistics();
      setQueuesOverallData((prevState) => {
        if (prevState.length === 0) {
          return initialStatistics;
        } else {
          return prevState;
        }
      });
    };

    fetchInitialStatistics();

    const interval = setInterval(() => {
      getQueuesListWithMessages();
    }, 1000);
    return () => {
      clearInterval(interval);
    };
  }, []);

  return {
    queuesData: queuesOverallData,
    fetchQueueMessages,
    deleteMessage,
    updateMessageExpandedState,
  };
}

export default useRefreshedQueueStatistics;
