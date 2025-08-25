import { useEffect, useState, useCallback } from "react";
import {
  getQueueMessages,
  getQueueListWithCorrelatedMessages,
  deleteMessage as deleteMessageService,
} from "../services/QueueService";
import { QueueMessagesData, QueueStatistic } from "./QueueMessageData";
import getErrorMessage from "../utils/getErrorMessage";

export default function useRefreshedQueueStatistics(): {
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
  const [queuesOverallData, setQueuesOverallData] = useState<
    QueueMessagesData[]
  >([]);

  const updateQueue = useCallback(
    (
      name: string,
      updater: (queue: QueueMessagesData) => QueueMessagesData
    ) => {
      setQueuesOverallData((prevQueues) =>
        prevQueues.map((queue) =>
          queue.queueName === name ? updater(queue) : queue
        )
      );
    },
    []
  );

  const fetchQueueMessages = useCallback(
    async (queueName: string) => {
      updateQueue(queueName, (queue) => ({
        ...queue,
        messagesLoading: true,
        messagesError: null,
      }));

      try {
        const messages = await getQueueMessages(queueName, 10);

        updateQueue(queueName, (queue) => ({
          ...queue,
          messages,
          messagesLoading: false,
          messagesError: null,
        }));
      } catch (error) {
        const messagesError = getErrorMessage(error);

        updateQueue(queueName, (queue) => ({
          ...queue,
          messagesLoading: false,
          messagesError,
        }));
      }
    },
    [updateQueue]
  );

  const updateMessageExpandedState = useCallback(
    (queueName: string, messageId: string | null) => {
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
    },
    []
  );

  const deleteMessage = useCallback(
    async (queueName: string, messageId: string, receiptHandle: string) => {
      try {
        await deleteMessageService(queueName, messageId, receiptHandle);

        await fetchQueueMessages(queueName);
      } catch (error) {
        console.error("Erro ao deletar mensagem:", error);
      }
    },
    [fetchQueueMessages]
  );

  async function obtainInitialStatistics() {
    const statistics = await getQueueListWithCorrelatedMessages();
    return statistics.map(convertQueueStatisticsToNewQueueData);
  }

  async function getQueuesListWithMessages() {
    const messages = await getQueueListWithCorrelatedMessages();

    setQueuesOverallData((prevState) => {
      return messages.map((queueStatistics) => {
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

  useEffect(() => {
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

function convertQueueStatisticsToNewQueueData(
  newQuery: QueueStatistic
): QueueMessagesData {
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
  };
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
