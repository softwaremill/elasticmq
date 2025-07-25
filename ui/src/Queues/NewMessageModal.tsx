import React, { useState } from "react";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Button,
} from "@material-ui/core";
import { useSnackbar } from "notistack";

import QueueService from "../services/QueueService";

interface NewMessageModalProps {
  open: boolean;
  onClose: () => void;
  queueName: string;
}

const NewMessageModal: React.FC<NewMessageModalProps> = ({
  open,
  onClose,
  queueName,
}) => {
  const [messageBody, setMessageBody] = useState("");
  const [loading, setLoading] = useState(false);
  const { enqueueSnackbar } = useSnackbar();

  const handleSendMessage = async () => {
    if (!messageBody.trim()) {
      enqueueSnackbar("Message body cannot be empty", {
        variant: "error",
      });
      return;
    }

    setLoading(true);
    try {
      await QueueService.sendMessage(queueName, messageBody);
      enqueueSnackbar("Message sent successfully!", {
        variant: "success",
      });
      setMessageBody("");
      onClose();
    } catch (error) {
      enqueueSnackbar("Failed to send message", {
        variant: "error",
      });
      console.error("Error sending message:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setMessageBody("");
    onClose();
  };

  return (
    <>
      <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
        <DialogTitle>New Message - {queueName}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Message Body"
            type="text"
            fullWidth
            multiline
            rows={6}
            variant="outlined"
            value={messageBody}
            onChange={(e) => setMessageBody(e.target.value)}
            placeholder="Enter your message body here..."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} color="secondary">
            Cancel
          </Button>
          <Button
            onClick={handleSendMessage}
            color="primary"
            variant="contained"
            disabled={loading}
          >
            {loading ? "Sending..." : "Send"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default NewMessageModal;
