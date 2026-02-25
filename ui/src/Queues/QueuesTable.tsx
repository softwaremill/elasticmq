import React from "react";
import TableContainer from "@material-ui/core/TableContainer";
import Paper from "@material-ui/core/Paper";
import Table from "@material-ui/core/Table";
import TableHead from "@material-ui/core/TableHead";
import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import { TableBody } from "@material-ui/core";
import "../styles/queue.css";
import QueueTableRow from "./QueueRow";
import useRefreshedQueueStatistics from "./RefreshQueuesData";

const QueuesTable: React.FC = () => {
  const {
    queuesData,
    fetchQueueMessages,
    deleteMessage,
    updateMessageExpandedState,
  } = useRefreshedQueueStatistics();

  return (
    <TableContainer component={Paper} elevation={2}>
      <Table size="small" aria-label="a dense table">
        <TableHead>
          <TableRow key="Queue table header">
            <TableCell />
            <TableCell>Name</TableCell>
            <TableCell align="right">Approximate number of messages</TableCell>
            <TableCell align="right">
              Approximate number of delayed messages
            </TableCell>
            <TableCell align="right">
              Approximate number of not visible Messages
            </TableCell>
            <TableCell align="center">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {queuesData.map((row) => (
            <QueueTableRow
              key={row.queueName}
              row={row}
              fetchQueueMessages={fetchQueueMessages}
              deleteMessage={deleteMessage}
              updateMessageExpandedState={updateMessageExpandedState}
            />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default QueuesTable;
