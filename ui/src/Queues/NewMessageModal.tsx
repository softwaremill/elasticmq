import React, { useState } from "react";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Button,
} from "@material-ui/core";
import { useSnackbar } from "../context/SnackbarContext";

import { sendMessage } from "../services/QueueService";
import getErrorMessage from "../utils/getErrorMessage";

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
  const { showSnackbar } = useSnackbar();

  const handleSendMessage = async () => {
    if (!messageBody.trim()) {
      showSnackbar("Message body cannot be empty");
      return;
    }

    setLoading(true);
    try {
      await sendMessage(queueName, messageBody);
      showSnackbar("Message sent successfully!");
      setMessageBody("");
      onClose();
    } catch (error) {
      const errorMessage = getErrorMessage(error);
      showSnackbar(`Failed to send message: ${errorMessage}`);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setMessageBody("");
    onClose();
  };

  return (
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
  );
};

export default NewMessageModal;
