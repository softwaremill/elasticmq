import React from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  Box,
  Button,
  Collapse,
  IconButton,
  Chip,
  CircularProgress,
} from "@material-ui/core";
import { Refresh, ExpandMore, ExpandLess, Delete } from "@material-ui/icons";
import { useSnackbar } from "../context/SnackbarContext";
import { QueueMessage } from "./QueueMessageData";
import getErrorMessage from "../utils/getErrorMessage";
import formatDate from "../utils/formatDate";
import truncateText from "../utils/truncateText";
import decodeHtmlEntities from "../utils/decodeHtml";

interface QueueMessagesListProps {
  queueName: string;
  messages?: QueueMessage[];
  loading?: boolean;
  error?: string | null;
  onRefreshMessages?: (queueName: string) => void;
  onDeleteMessage?: (
    queueName: string,
    messageId: string,
    receiptHandle: string
  ) => Promise<void>;
  updateMessageExpandedState: (
    queueName: string,
    messageId: string | null
  ) => void;
}

const QueueMessagesList: React.FC<QueueMessagesListProps> = ({
  queueName,
  messages = [],
  loading = false,
  error = null,
  onRefreshMessages,
  onDeleteMessage,
  updateMessageExpandedState,
}) => {
  const { showSnackbar } = useSnackbar();

  const toggleMessageExpansion = (messageId: string) => {
    updateMessageExpandedState(queueName, messageId);
  };

  const handleDeleteMessage = async (
    messageId: string,
    receiptHandle?: string
  ) => {
    if (!receiptHandle) {
      showSnackbar("Cannot delete message without receiptHandle");
      return;
    }

    if (onDeleteMessage) {
      try {
        await onDeleteMessage(queueName, messageId, receiptHandle);
        showSnackbar("Message deleted successfully");
      } catch (error) {
        const errorMessage = getErrorMessage(error);
        showSnackbar(`Failed to delete message: ${errorMessage}`);
      }
    }
  };

  return (
    <Box margin={1}>
      <Box
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        mb={2}
      >
        <Box display="flex" alignItems="center">
          <Typography
            variant="h6"
            gutterBottom
            style={{ margin: 0, marginRight: 8 }}
          >
            Messages ({messages?.length || 0})
          </Typography>
          {loading && <CircularProgress size={16} />}
        </Box>
        {onRefreshMessages && (
          <Button
            startIcon={<Refresh />}
            onClick={() => onRefreshMessages(queueName)}
            disabled={loading}
            size="small"
            variant="outlined"
          >
            {loading ? "Loading..." : "Refresh"}
          </Button>
        )}
      </Box>

      {error && (
        <Box mb={2}>
          <Typography variant="body2" color="error">
            {error}
          </Typography>
        </Box>
      )}

      {(!messages || messages.length === 0) && !loading ? (
        <Typography variant="body2" color="textSecondary">
          No messages found in this queue.
        </Typography>
      ) : (
        <Table size="small" aria-label="queue messages">
          <TableHead>
            <TableRow>
              <TableCell width="40px" />
              <TableCell>Message ID</TableCell>
              <TableCell>Body Preview</TableCell>
              <TableCell>Sent Time</TableCell>
              <TableCell>Attributes</TableCell>
              <TableCell width="80px">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {messages?.map((message) => (
              <React.Fragment key={message.messageId}>
                <TableRow>
                  <TableCell>
                    <IconButton
                      size="small"
                      onClick={() => toggleMessageExpansion(message.messageId)}
                    >
                      {message.isExpanded ? <ExpandLess /> : <ExpandMore />}
                    </IconButton>
                  </TableCell>
                  <TableCell>
                    <Typography
                      variant="body2"
                      style={{ fontFamily: "monospace" }}
                    >
                      {message.messageId.substring(0, 20)}...
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {truncateText(decodeHtmlEntities(message.body))}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {formatDate(message.sentTimestamp)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {message.attributes &&
                    Object.keys(message.attributes).length > 0 ? (
                      <Chip
                        size="small"
                        label={`${
                          Object.keys(message.attributes).length
                        } attrs`}
                        variant="outlined"
                      />
                    ) : (
                      <Typography variant="body2" color="textSecondary">
                        None
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <IconButton
                      size="small"
                      color="secondary"
                      onClick={() =>
                        handleDeleteMessage(
                          message.messageId,
                          message.receiptHandle
                        )
                      }
                      disabled={!message.receiptHandle || loading}
                      title="Delete message"
                    >
                      <Delete />
                    </IconButton>
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell
                    style={{ paddingBottom: 0, paddingTop: 0 }}
                    colSpan={6}
                  >
                    <Collapse
                      in={message.isExpanded}
                      timeout="auto"
                      unmountOnExit
                    >
                      <Box margin={1}>
                        <Typography variant="subtitle2" gutterBottom>
                          Full Message Details:
                        </Typography>
                        <Box mb={2}>
                          <Typography
                            variant="body2"
                            style={{ fontWeight: "bold" }}
                          >
                            Message ID:
                          </Typography>
                          <Typography
                            variant="body2"
                            style={{
                              fontFamily: "monospace",
                              backgroundColor: "#f5f5f5",
                              padding: "4px",
                            }}
                          >
                            {message.messageId}
                          </Typography>
                        </Box>
                        <Box mb={2}>
                          <Typography
                            variant="body2"
                            style={{ fontWeight: "bold" }}
                          >
                            Body:
                          </Typography>
                          <Typography
                            variant="body2"
                            style={{
                              fontFamily: "monospace",
                              backgroundColor: "#f5f5f5",
                              padding: "8px",
                              whiteSpace: "pre-wrap",
                            }}
                          >
                            {decodeHtmlEntities(message.body)}
                          </Typography>
                        </Box>
                        {message.attributes &&
                          Object.keys(message.attributes).length > 0 && (
                            <Box mb={2}>
                              <Typography
                                variant="body2"
                                style={{ fontWeight: "bold" }}
                              >
                                Attributes:
                              </Typography>
                              <Table size="small">
                                <TableBody>
                                  {Object.entries(message.attributes).map(
                                    ([key, value]) => (
                                      <TableRow key={key}>
                                        <TableCell
                                          style={{ fontFamily: "monospace" }}
                                        >
                                          {key}
                                        </TableCell>
                                        <TableCell
                                          style={{ fontFamily: "monospace" }}
                                        >
                                          {value}
                                        </TableCell>
                                      </TableRow>
                                    )
                                  )}
                                </TableBody>
                              </Table>
                            </Box>
                          )}
                        {message.messageAttributes &&
                          Object.keys(message.messageAttributes).length > 0 && (
                            <Box mb={2}>
                              <Typography
                                variant="body2"
                                style={{ fontWeight: "bold" }}
                              >
                                Message Attributes:
                              </Typography>
                              <Table size="small">
                                <TableBody>
                                  {Object.entries(
                                    message.messageAttributes
                                  ).map(([key, value]) => (
                                    <TableRow key={key}>
                                      <TableCell
                                        style={{ fontFamily: "monospace" }}
                                      >
                                        {key}
                                      </TableCell>
                                      <TableCell
                                        style={{ fontFamily: "monospace" }}
                                      >
                                        {JSON.stringify(value, null, 2)}
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </Box>
                          )}
                      </Box>
                    </Collapse>
                  </TableCell>
                </TableRow>
              </React.Fragment>
            ))}
          </TableBody>
        </Table>
      )}
    </Box>
  );
};

export default React.memo(QueueMessagesList);
