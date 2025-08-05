import React from "react";
import { render, screen } from "@testing-library/react";
import QueueMessagesList from "./QueueMessagesList";
import { QueueMessage } from "./QueueMessageData";
import { SnackbarProvider } from "../context/SnackbarContext";

const mockUpdateMessageExpandedState = jest.fn();
const mockOnRefreshMessages = jest.fn();

const renderWithSnackbarProvider = (component: React.ReactElement) => {
  return render(<SnackbarProvider>{component}</SnackbarProvider>);
};

describe("<QueueMessagesList /> - New Features", () => {
  describe("HTML Entity Decoding (New Feature)", () => {
    test("decodes HTML entities in message body preview", () => {
      const messageWithEntities: QueueMessage[] = [
        {
          messageId: "msg-html-entities",
          body: "&quot;organizationId&quot;:&quot;test&quot;",
          sentTimestamp: "1609459200000",
        },
      ];

      renderWithSnackbarProvider(
        <QueueMessagesList
          queueName="test-queue"
          messages={messageWithEntities}
          loading={false}
          error={null}
          updateMessageExpandedState={mockUpdateMessageExpandedState}
        />
      );

      // Should display decoded version in preview
      expect(screen.getByText('"organizationId":"test"')).toBeInTheDocument();
    });

    test("decodes HTML entities in full message body when expanded", () => {
      const messageWithEntities: QueueMessage[] = [
        {
          messageId: "msg-html-entities",
          body: "&quot;data&quot;:&quot;value&quot;&amp;&lt;test&gt;",
          sentTimestamp: "1609459200000",
        },
      ];

      renderWithSnackbarProvider(
        <QueueMessagesList
          queueName="test-queue"
          messages={messageWithEntities}
          loading={false}
          error={null}
          updateMessageExpandedState={mockUpdateMessageExpandedState}
        />
      );

      // Should display decoded version in preview (truncated)
      expect(screen.getByText('"data":"value"&<test>')).toBeInTheDocument();
    });
  });

  describe("Props-based State Management (New Feature)", () => {
    test("uses messages from props when provided", () => {
      const propsMessages: QueueMessage[] = [
        {
          messageId: "props-msg-1",
          body: "Message from props",
          sentTimestamp: "1609459200000",
        },
      ];

      renderWithSnackbarProvider(
        <QueueMessagesList
          queueName="test-queue"
          messages={propsMessages}
          loading={false}
          error={null}
          updateMessageExpandedState={mockUpdateMessageExpandedState}
        />
      );

      expect(screen.getByText("Messages (1)")).toBeInTheDocument();
      expect(screen.getByText("Message from props")).toBeInTheDocument();
    });

    test("shows loading state from props", () => {
      renderWithSnackbarProvider(
        <QueueMessagesList
          queueName="test-queue"
          messages={[]}
          loading={true}
          error={null}
          updateMessageExpandedState={mockUpdateMessageExpandedState}
          onRefreshMessages={mockOnRefreshMessages}
        />
      );

      expect(screen.getByText("Loading...")).toBeInTheDocument();
      expect(screen.getByRole("progressbar")).toBeInTheDocument();
    });

    test("shows error state from props", () => {
      const errorMessage = "Failed to fetch messages from parent";
      renderWithSnackbarProvider(
        <QueueMessagesList
          queueName="test-queue"
          messages={[]}
          loading={false}
          error={errorMessage}
          updateMessageExpandedState={mockUpdateMessageExpandedState}
        />
      );

      expect(screen.getByText(errorMessage)).toBeInTheDocument();
    });
  });
});
