import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import IconButton from "@material-ui/core/IconButton";
import {
  AddComment,
  KeyboardArrowDown,
  KeyboardArrowRight,
} from "@material-ui/icons";
import React, { useState } from "react";
import { QueueMessagesData } from "./QueueMessageData";
import RowDetails from "./QueueRowDetails";
import NewMessageModal from "./NewMessageModal";

function QueueTableRow({
  row,
  fetchQueueMessages,
  deleteMessage,
  updateMessageExpandedState,
}: {
  row: QueueMessagesData;
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
}) {
  const [isExpanded, setIsExpanded] = useState<boolean>(false);
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);

  return (
    <>
      <TableRow key={row.queueName} className={"queue-row"}>
        <TableCell>
          <IconButton
            aria-label="open-details"
            size="small"
            onClick={() => setIsExpanded((prevState) => !prevState)}
          >
            {isExpanded ? <KeyboardArrowRight /> : <KeyboardArrowDown />}
          </IconButton>
        </TableCell>
        <TableCell component="th" scope="row">
          {row.queueName}
        </TableCell>
        <TableCell align="right">{row.currentMessagesNumber}</TableCell>
        <TableCell align="right">{row.delayedMessagesNumber}</TableCell>
        <TableCell align="right">{row.notVisibleMessagesNumber}</TableCell>
        <TableCell align="center">
          <IconButton
            size="small"
            color="primary"
            onClick={() => setIsModalOpen(true)}
            title="New message"
          >
            <AddComment />
          </IconButton>
        </TableCell>
      </TableRow>
      <RowDetails
        isExpanded={isExpanded}
        queueName={row.queueName}
        queueData={row}
        fetchQueueMessages={fetchQueueMessages}
        deleteMessage={deleteMessage}
        updateMessageExpandedState={updateMessageExpandedState}
      />
      <NewMessageModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        queueName={row.queueName}
      />
    </>
  );
}

export default QueueTableRow;
